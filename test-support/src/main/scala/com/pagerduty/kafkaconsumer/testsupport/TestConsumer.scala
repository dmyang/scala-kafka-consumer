package com.pagerduty.kafkaconsumer.testsupport

import org.apache.kafka.clients.consumer.ConsumerRecords
import com.pagerduty.kafkaconsumer.SimpleKafkaConsumer
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object TestConsumerConfig {
  // Consumer group name
  val consumerGroup = "kafkaconsumer-it-consumer"

  // Simple helper to create properties from the above. Note that
  // we don't cache the lookup, as it may always change.
  def makeProps(maxPollRecords: Option[Int] = None) = {
    val props = SimpleKafkaConsumer.makeProps(
      "localhost:9092",
      TestConsumerConfig.consumerGroup,
      maxPollRecords
    )
    // Make stuff fail a bit quicker than normal
    props.put("session.timeout.ms", "6000")
    props.put("heartbeat.interval.ms", "1000")
    props.put("auto.offset.reset", "earliest")
    props
  }
}

class TestConsumer(
  topic: String,
  pollTimeout: Duration = 100 milliseconds,
  restartOnExceptionDelay: Duration = SimpleKafkaConsumer.restartOnExceptionDelay,
  maxPollRecords: Option[Int] = None
)
    extends SimpleKafkaConsumer(
      topic,
      TestConsumerConfig.makeProps(maxPollRecords),
      pollTimeout = pollTimeout,
      restartOnExceptionDelay = restartOnExceptionDelay
    )
    with ConsumerTestHelper {

  private var keys = Set.empty[Long]
  def processedKeys: Set[Long] = this.synchronized { keys }
  private def recordKey(key: String) = this.synchronized {
    try {
      keys += key.toLong
    } catch {
      case _: NumberFormatException => // ignore
    }
  }

  private var keyGroups = List.empty[List[Long]]
  def processedKeyGroups: Seq[Seq[Long]] = this.synchronized { keyGroups }

  override protected def processRecords(records: ConsumerRecords[String, String]): Unit = {
    var keyGroup = List.empty[Long]
    for (record <- records) {
      log.debug(s"process ${record.topic}/${record.partition}/${record.offset}: ${record.key}=${record.value}")
      processMessage(record.key, record.value)
      try {
        keyGroup = keyGroup :+ record.key.toLong
      } catch {
        case _: NumberFormatException => // ignore
      }
    }
    if (keyGroup.length > 0) {
      keyGroups = keyGroups :+ keyGroup
    }

    autoShutdownWhenInactive(records.count)
  }

  protected def processMessage(key: String, value: String): Unit = {
    recordKey(key)
  }
}

trait ConsumerTestHelper { self: SimpleKafkaConsumer[_, _] =>
  private var emptyPollCount = 0
  private val inactivityForShutdown = 2.seconds
  private val maxEmptyPollCount = (inactivityForShutdown / pollTimeout).toInt

  protected def autoShutdownWhenInactive(pollResultsCount: Int): Unit = {
    if (pollResultsCount == 0) emptyPollCount += 1 else emptyPollCount = 0
    if (emptyPollCount > maxEmptyPollCount) {
      log.info("Shutting down consumer due to inactivity.")
      shutdown()
    }
  }

  def awaitTermination(): Unit = {
    val statusPollIntervalMs = 100
    while (!hasTerminated) {
      Thread.sleep(statusPollIntervalMs)
    }
  }
}

class ShutdownTestConsumer(
  topic: String,
  pollTimeout: Duration = 100 milliseconds,
  restartOnExceptionDelay: Duration = SimpleKafkaConsumer.restartOnExceptionDelay
)
    extends SimpleKafkaConsumer(
      topic,
      TestConsumerConfig.makeProps(),
      pollTimeout = pollTimeout,
      restartOnExceptionDelay = restartOnExceptionDelay
    ) {
  override protected def processRecords(records: ConsumerRecords[String, String]): Unit = {}

  def awaitTerminationAndTrackDelay(): Long = {
    val statusPollIntervalMs = 10
    val start = System.currentTimeMillis
    while (!hasTerminated) {
      Thread.sleep(statusPollIntervalMs)
    }
    System.currentTimeMillis - start
  }
}
