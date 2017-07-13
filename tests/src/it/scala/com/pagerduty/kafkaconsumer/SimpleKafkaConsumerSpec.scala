package com.pagerduty.kafkaconsumer

import org.apache.kafka.clients.consumer.ConsumerRecords
import org.scalatest.concurrent.Eventually
import org.scalatest.{FreeSpec, Matchers}
import com.pagerduty.kafkaconsumer.testsupport.{TestProducer, ShutdownTestConsumer, TestConsumer, KafkaConsumerSpec}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class SimpleKafkaConsumerSpec extends FreeSpec with Matchers with KafkaConsumerSpec with Eventually {
  protected val topic = "kafkaconsumer_it_topic"

  object testProducer extends TestProducer(topic) {
    def sendTestMessage(id: Long): Unit = {
      val message = s"body_$id"
      send(id.toString, message)
    }
    def sendTestMessages(ids: Seq[Long]): Unit = {
      for (id <- ids) sendTestMessage(id)
    }
  }

  "SimpleKafkaConsumer should" - {
    "process messages" in {
      val consumer = new TestConsumer(topic)
      consumer.start()

      val ids = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      consumer.awaitTermination()
      consumer.processedKeys shouldBe ids.toSet
    }

    "auto-restart on errors" in {
      val failOnceConsumer = makeFailOnceConsumer(restartDelay = 1.second)
      failOnceConsumer.start()

      val ids = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      failOnceConsumer.awaitTermination()
      failOnceConsumer.processedKeys shouldBe ids.toSet
    }

    "not commit until message processing is finished" in {
      // `failAndShutdownConsumer` will poll some messages and then throw and exception.
      // The consumer offset should not be advanced, so these messages can be consumed later.
      val failAndShutdownConsumer = makeFailAndShutdownConsumer()
      failAndShutdownConsumer.start()

      val ids = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)
      failAndShutdownConsumer.awaitTermination()

      val consumer = new TestConsumer(topic)
      consumer.start()
      consumer.awaitTermination()
      consumer.processedKeys shouldBe ids.toSet
    }

    "complete shutdown future when terminated" in {
      val consumer = new ShutdownTestConsumer(topic)
      consumer.start()

      val shutdownFuture = consumer.shutdown()
      Await.result(shutdownFuture, atMost = 1.second)
    }

    "complete shutdown future when shutdown() is called before start()" in {
      val consumer = new ShutdownTestConsumer(topic)
      val shutdownFuture = consumer.shutdown()
      consumer.start()
      Await.result(shutdownFuture, atMost = 1.second)
    }

    "shutdown quickly when polling" in {
      val maxAcceptableShutdownDuration = 1.second
      val pollDuration = 30.seconds
      // NOTE: pollDuration must be much greater than the acceptable shutdown duration.

      val longPollConsumer = makeConsumer(pollDuration)
      longPollConsumer.start()

      val waitForConsumerToBlock = 1.second
      Thread.sleep(waitForConsumerToBlock.toMillis)
      // At this stage, consumer should be blocked on poll().

      longPollConsumer.shutdown()
      val shutdownTime = longPollConsumer.awaitTerminationAndTrackDelay()
      shutdownTime should be < maxAcceptableShutdownDuration.toMillis
    }

    "shutdown quickly when waiting to restart" in {
      val maxAcceptableShutdownDuration = 1.second
      val restartDelay = 30.seconds
      // NOTE: restartDelay must be much greater than the acceptable shutdown duration.

      val restartOnMessageConsumer = makeFailingConsumer(restartDelay)
      restartOnMessageConsumer.start()

      testProducer.sendTestMessage(id = 1)
      val waitForConsumerToBlock = 1.second
      Thread.sleep(waitForConsumerToBlock.toMillis)
      // At this stage, consumer thread should be suspended waiting for restart.

      restartOnMessageConsumer.shutdown()
      val shutdownTime = restartOnMessageConsumer.awaitTerminationAndTrackDelay()
      shutdownTime should be < maxAcceptableShutdownDuration.toMillis
    }

    "have partition count after first connection" in {
      val consumer = new TestConsumer(topic)
      consumer.partitionCount shouldBe None

      consumer.start()
      eventually(timeout(5 seconds)) {
        consumer.partitionCount shouldBe Some(1)
      }

      Await.result(consumer.shutdown(), 10.seconds)
      consumer.partitionCount shouldBe Some(1)
    }

    "support max.poll.records" in {
      val consumer = new TestConsumer(topic, pollTimeout = 10.seconds, maxPollRecords = Option(4))
      consumer.start()

      val ids: Seq[Long] = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      consumer.awaitTermination()

      val expectedIdGroups: Seq[Seq[Long]] = ids.grouped(4).toSeq
      consumer.processedKeyGroups shouldBe expectedIdGroups
    }

    "After to many exceptions in a short time period, the consumer dies" in {
      val restartDelay = 1.second
      val restarts = 2
      val badConsumer = makeFailingConsumer(restartDelay = restartDelay, maxRestarts = restarts, restartInterval = 100 minutes)
      badConsumer.start()

      val ids: Seq[Long] = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      val delay = badConsumer.awaitTerminationAndTrackDelay()

      delay should be < (restartDelay * restarts + 10.second /* 10s buffer */).toMillis
    }
  }

  def makeMessageIdSeq(count: Int): Seq[Long] = {
    val timeStamp = System.currentTimeMillis
    timeStamp.until(timeStamp + count)
  }

  def makeFailOnceConsumer(restartDelay: Duration): TestConsumer =
    new TestConsumer(topic, restartOnExceptionDelay = restartDelay) {
      private var hasFailedOnce = false

      override protected def processMessage(key: String, value: String): Unit = {
        if (!hasFailedOnce) {
          hasFailedOnce = true
          throw new RuntimeException("Simulated consumer exception.")
        }
        super.processMessage(key, value)
      }

    }

  def makeFailAndShutdownConsumer(): TestConsumer =
    new TestConsumer(topic, restartOnExceptionDelay = 1 second) {
      override protected def processMessage(key: String, value: String): Unit = {
        shutdown()
        throw new RuntimeException("Simulated consumer exception.")
      }
    }

  def makeConsumer(pollTimeout: Duration): ShutdownTestConsumer =
    new ShutdownTestConsumer(topic, pollTimeout = pollTimeout)

  def makeFailingConsumer(restartDelay: Duration, maxRestarts: Int = SimpleKafkaConsumer.maxRestartsInIntervalDueToExceptions, restartInterval: Duration = SimpleKafkaConsumer.restartDueToExceptionsInterval): ShutdownTestConsumer =
    new ShutdownTestConsumer(topic, restartOnExceptionDelay = restartDelay, maxRestartsInIntervalDueToExceptions = maxRestarts, restartDueToExceptionsInterval = restartInterval) {
      override protected def processRecords(records: ConsumerRecords[String, String]): Unit = {
        throw new RuntimeException("Simulated consumer exception.")
      }
    }

}
