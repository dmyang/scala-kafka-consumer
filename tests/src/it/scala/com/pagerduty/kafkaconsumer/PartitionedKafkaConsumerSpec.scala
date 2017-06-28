package com.pagerduty.kafkaconsumer

import com.pagerduty.kafkaconsumer.testsupport.{KafkaConsumerSpec, TestProducer}
import org.apache.kafka.clients.consumer.{ConsumerRecord}
import org.scalatest.concurrent.Eventually
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class PartitionedKafkaConsumerSpec extends FreeSpec with Matchers with KafkaConsumerSpec with Eventually {
  protected val topic = "partitionedkafkaconsumer_it_topic"

  object testProducer extends TestProducer(topic) {
    def sendTestMessage(id: Long): Unit = {
      val message = s"body_$id"
      send(id.toString, message)

    }
    def sendTestMessages(ids: Seq[Long]): Unit = {
      for (id <- ids) sendTestMessage(id)
    }
  }

  "PartitionedKafkaConsumer should" - {
    "throw an exception if started when already running" in {
      val consumer = new TestConsumer
      consumer.start()
      an[IllegalStateException] should be thrownBy (consumer.start())
      consumer.shutdown()
    }

    "process messages" in {
      val consumer = new TestConsumer
      consumer.start()

      val ids = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      eventually(timeout(25.seconds)) {
        consumer.processedKeys shouldBe ids.toSet
      }

      consumer.shutdown()
    }

    "auto-restart on errors" in {
      val failOnceConsumer = makeFailOnceConsumer(restartDelay = 1.second)
      failOnceConsumer.start()

      val ids = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      eventually(timeout(25.seconds)) {
        failOnceConsumer.processedKeys shouldBe ids.toSet
      }

      failOnceConsumer.shutdown()
    }

    "not commit until message processing is finished" in {
      // `restartOnMessageConsumer` will poll some messages and then throw and exception.
      // The consumer offset should not be advanced, so these messages can be consumed later.
      val restartOnMessageConsumer = makeFailingConsumer()
      restartOnMessageConsumer.start()

      val ids = makeMessageIdSeq(10)
      testProducer.sendTestMessages(ids)

      val consumer = new TestConsumer
      consumer.start()
      eventually(timeout(25.seconds)) {
        consumer.processedKeys shouldBe ids.toSet
      }
      consumer.shutdown()
      restartOnMessageConsumer.shutdown()
    }

    "shutdown quickly when waiting to restart" in {
      val restartOnMessageConsumer = makeFailingConsumer()
      restartOnMessageConsumer.start()

      testProducer.sendTestMessage(id = 1)
      val waitForConsumerToFail = 1.second
      Thread.sleep(waitForConsumerToFail.toMillis)
      // At this stage, consumer should be waiting to restart
      val shutdown = Future {
        restartOnMessageConsumer.shutdown()
      }

      Await.ready(shutdown, 5.seconds)
    }
  }

  class TestConsumer(restartDelay: FiniteDuration = 5.seconds)
      extends PartitionedKafkaConsumer(
        topic = topic,
        restartOnExceptionDelay = restartDelay
      ) {
    protected def processRecord(record: ConsumerRecord[String, String], partition: Int): Future[Unit] = {
      Future {
        recordKey(record.key())
      }
    }

    @volatile private var keys = Set.empty[Long]
    def processedKeys: Set[Long] = this.synchronized { keys }
    private def recordKey(key: String) = this.synchronized {
      try {
        keys += key.toLong
      } catch {
        case _: NumberFormatException => // ignore
      }
    }
  }

  def makeMessageIdSeq(count: Int): Seq[Long] = {
    val timeStamp = System.currentTimeMillis
    timeStamp.until(timeStamp + count)
  }

  def makeFailOnceConsumer(restartDelay: FiniteDuration): TestConsumer =
    new TestConsumer(restartDelay) {
      private var hasFailedOnce = false

      override protected def processRecord(record: ConsumerRecord[String, String], partition: Int): Future[Unit] = {
        if (!hasFailedOnce) {
          hasFailedOnce = true
          Future.failed(new RuntimeException("Simulated consumer exception."))
        } else {
          super.processRecord(record, partition)
        }
      }
    }

  def makeFailingConsumer(): TestConsumer =
    new TestConsumer(20 second) {
      override protected def processRecord(record: ConsumerRecord[String, String], partition: Int): Future[Unit] = {
        throw new RuntimeException("Simulated consumer exception.")
      }
    }
}
