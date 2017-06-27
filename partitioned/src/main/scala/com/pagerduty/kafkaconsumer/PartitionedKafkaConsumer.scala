package com.pagerduty.kafkaconsumer

import akka.Done
import akka.actor.{ActorSystem, Cancellable}
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}

/**
  * The PartitionedKafkaConsumer is an alternative to SimpleKafkaConsumer.
  *
  * The advantage it has is that partitions are consumed, processed, and committed independently from each other. So, a
  * partition containing messages that take a long time to process will not slow down the processing of other partitions.
  *
  * Unlike SimpleKafkaConsumer, this consumer parallelizes the processing of messages from different partitions. This means
  * there will also be greater throughput from this consumer since it can properly use multiple CPU cores.
  *
  * Users of this class should call `start()` to begin consuming records from Kafka, and `shutdown()` to stop consuming.
  * `start()` may be called again after `shutdown()`.
  *
  * @param topic The topic from which to consume
  * @param keyDeserializer The Kafka record key deserializer
  * @param valueDeserializer The Kafka record value deserializer
  * @param config Optional Typesafe config containing the `akka.kafka.consumer` config structure. This is used to tune the
  *               underlying akka-stream-kafka library, and the Kafka client library.
  *
  *               See http://doc.akka.io/docs/akka-stream-kafka/current/consumer.html for details
  * @param restartOnExceptionDelay How long to wait before restarting the consumer after an unhandled exception
  * @param commitOffsetMaxBatchSize The maximum size of a batch of records before they are committed back to Kafka
  * @param consumerShutdownTimeout Timeout on shutdown before the consumer is forcibly terminated
  * @tparam K The type of the Kafka record key
  * @tparam V The type of the Kafka record value
  */
abstract class PartitionedKafkaConsumer[K, V](
    topic: String,
    keyDeserializer: Deserializer[K],
    valueDeserializer: Deserializer[V],
    config: Config = ConfigFactory.load(),
    restartOnExceptionDelay: FiniteDuration = PartitionedKafkaConsumer.RestartOnExceptionDelay,
    commitOffsetMaxBatchSize: Long = PartitionedKafkaConsumer.CommitOffsetMaxBatchSize,
    consumerShutdownTimeout: Duration = PartitionedKafkaConsumer.ConsumerShutdownTimeout) {

  protected val log = LoggerFactory.getLogger(getClass)

  private object Lock

  @volatile private var optSystem: Option[ActorSystem] = None
  @volatile private var optConsumer: Option[Consumer.Control] = None
  @volatile private var optFutureConsumerStartup: Option[Cancellable] = None

  private val decider: Supervision.Decider = { e =>
    log.warn(s"Unhandled exception when consuming or processing Kafka message from topic $topic: ", e)
    restartConsumer()
    Supervision.Stop
  }

  /**
    * This method causes the consumer to start consuming records from Kafka.
    *
    * @throws IllegalStateException if the consumer has already been started
    */
  def start(): Unit = Lock.synchronized {
    if (optSystem.isDefined || optConsumer.isDefined)
      throw new IllegalStateException("PartitionedKafkaConsumer has already been started!")

    log.info(s"Starting PartitionedKafkaConsumer on topic: $topic...")

    optSystem = Some(ActorSystem("PartitionedKafkaConsumer", config))

    startConsumer()
    log.info("PartitionedKafkaConsumer start-up is complete")
  }

  /**
    * This method stops the consumer from consuming records from Kafka. It is a graceful shutdown, so records being
    * processed will have `consumerShutdownTimeout` to complete before they are forcibly terminated.
    *
    * If the consumer is already shutdown, calling this method has no effect.
    */
  def shutdown(): Unit = Lock.synchronized {
    log.info("Shutting down PartitionedKafkaConsumer...")
    shutdownConsumer()
    shutdownSystem()
    log.info("PartitionedKafkaConsumer shutdown is complete")
  }

  /**
    * This method is the only method that must be overridden to use this class.
    *
    * Logic to process an individual record from Kafka should be placed in this method. Once the method completes successfully,
    * its offset will be committed asynchronously back to Kafka, subject to batching rules. Because of the batched and async commit,
    * and because of the at-least-once characteristics of Kafka, this method should handle being called with the same record
    * multiple times.
    *
    * If this method throws an exception, its offset will not be committed back to Kafka. Rather, the underlying Kafka
    * consumer will be restarted after `restartOnExceptionDelay`. The same record will then be passed to this method.
    * This allows for transient exceptions (e.g. network timeouts) to be handled gracefully. Non-transient exceptions,
    * however, will cause this consumer to go into a restart loop, meaning that no records (even from other partitions)
    * will be processed.
    *
    * This method should return a Future, completed when the record is processed. If your processing logic is synchronous,
    * simply wrap it in a Future (probably with `blocking`) and let it be executed by a thread pool or other ExecutionContext.
    * The point of this interface is to be flexible with respect to concurrency.
    *
    * @param record The Kafka record to process
    * @param partition The partition from which the record was consumed
    *
    * @return A Future that, when complete, indicates that the record has been successfully processed
    */
  def processRecord(record: ConsumerRecord[K, V], partition: Int): Future[Unit]

  /**
    * This method can be overridden to hook in custom behaviour after a Kafka record has been successfully
    * processed and committed.
    *
    * @param record The record for which an offset was committed
    * @param partition The partition for which the offset was committed
    * @param offset The offset which was committed
    *
    * @return A Future that, when complete, indicates that the custom logic has been completed
    */
  def afterCommit(record: ConsumerRecord[K, V], partition: Int, offset: Long): Future[Unit] = {
    Future.successful(Unit)
  }

  private def startConsumer(): Unit = Lock.synchronized {
    if (optConsumer.isDefined) {
      throw new IllegalStateException("Can't start a consumer if one exists already!")
    }

    log.info("Starting Kafka consumer...")
    implicit val system = optSystem.get
    implicit val ec = system.dispatcher

    val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
    implicit val materializer = ActorMaterializer(materializerSettings)

    val consumerSettings = ConsumerSettings(system, keyDeserializer, valueDeserializer)

    val subscriptions = Subscriptions.topics(topic)

    val (consumerControl, done) = buildConsumerSource(consumerSettings, subscriptions)
      .toMat(Sink.ignore)(Keep.both)
      .run()

    optConsumer = Some(consumerControl)

    done.onComplete {
      case Success(_) => log.info("Kafka consumer is successfully shutdown")
      case Failure(e) => log.warn(s"Kafka consumer has failed on topic $topic!", e)
    }
    log.info("Kafka consumer start-up is complete")
  }

  private def buildConsumerSource(
      consumerSettings: ConsumerSettings[K, V],
      subscriptions: AutoSubscription
    )(implicit executionContext: ExecutionContext,
      materializer: ActorMaterializer
    ): Source[Future[Done], Consumer.Control] = {
    Consumer
      .committablePartitionedSource(consumerSettings, subscriptions)
      .map {
        case (topicPartition, source) =>
          val partition = topicPartition.partition()
          source
            .mapAsync(1) { msg =>
              processRecord(msg.record, partition).map(_ => msg)
            }
            .batch(
              max = commitOffsetMaxBatchSize,
              first =>
                (
                  Seq(first),
                  CommittableOffsetBatch.empty.updated(first.committableOffset)
              )
            ) {
              case ((batchedRecords, batchedOffsets), elem) =>
                val r = batchedRecords :+ elem
                (r, batchedOffsets.updated(elem.committableOffset))
            }
            .mapAsync(1) {
              case (batchedRecords, committableBatch) =>
                committableBatch.commitScaladsl().map(_ => (batchedRecords, committableBatch))
            }
            .runForeach {
              case (batchedRecords, offsetInfo) =>
                batchedRecords.zip(offsetInfo.offsets) foreach {
                  case (committedRecord, (_: ConsumerMessage.GroupTopicPartition, offset: Long)) =>
                    afterCommit(committedRecord.record, partition, offset)
                }
            }

      }
  }

  private def restartConsumer(): Unit = Lock.synchronized {
    log.info("Restarting Kafka consumer...")

    // the consumer might be shutdown already (through user-initiated shutdown), in which case this is a no-op
    shutdownConsumer()

    // it's possible that the system is already shutdown (through user-initiated shutdown)
    // in this case, don't schedule the consumer startup
    optSystem match {
      case Some(system) =>
        log.info(s"Scheduling Kafka consumer start-up for $restartOnExceptionDelay in the future")
        optFutureConsumerStartup = Some(system.scheduler.scheduleOnce(restartOnExceptionDelay) {
          startConsumer()
          optFutureConsumerStartup = None
        }(system.dispatcher))
      case None =>
        log.info("Not scheduling a Kafka consumer start-up because shutdown has been initiated")
    }
  }

  private def shutdownConsumer() = Lock.synchronized {
    optFutureConsumerStartup match {
      case Some(futureConsumerStartup) =>
        log.info("Cancelling scheduled Kafka consumer startup")
        futureConsumerStartup.cancel()
      case None => // nothing to do
    }

    optConsumer match {
      case Some(consumer) =>
        log.info("Shutting down Kafka consumer...")
        Await.ready(consumer.shutdown(), consumerShutdownTimeout)
        optConsumer = None
        log.info("Kafka consumer shutdown complete")
      case None =>
        log.info("Skipping Kafka consumer shutdown, since it's already done")
    }
  }

  private def shutdownSystem() = Lock.synchronized {
    optSystem match {
      case Some(system) =>
        log.info("Shutting down underlying actor system...")
        Await.ready(system.terminate(), PartitionedKafkaConsumer.SystemShutdownTimeout)
        optSystem = None
        log.info("Underlying actor system shutdown complete")
      case None =>
        log.info("Skipping underlying actor system shutdown, since it's already done")
    }
  }
}

object PartitionedKafkaConsumer {
  import scala.concurrent.duration._

  // Default values
  val CommitOffsetMaxBatchSize = 20L
  val ConsumerShutdownTimeout = 5.seconds
  val SystemShutdownTimeout = 1.second
  val RestartOnExceptionDelay = 5.seconds
}
