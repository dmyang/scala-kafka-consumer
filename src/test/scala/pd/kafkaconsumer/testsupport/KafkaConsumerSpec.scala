package pd.kafkaconsumer.testsupport

import org.scalatest._
import org.slf4j.LoggerFactory


trait KafkaConsumerSpec extends BeforeAndAfterEachTestData with BeforeAndAfterAll { this: Suite =>
  protected val log = LoggerFactory.getLogger(this.getClass)

  protected def topic: String
  protected val testProducer: TestProducer

  override protected def afterAll(): Unit = {
    super.afterAll()
    testProducer.shutdown()
  }

  override def beforeEach(testData: TestData) = {
    log.debug(s"Starting test '${testData.name}'.")
    drainOldMessages()
  }
  override def afterEach(testData: TestData) = {
    log.debug(s"Finished test '${testData.name}'.")
  }

  def drainOldMessages(): Unit = {
    val consumer = new CleanupConsumer(topic)
    consumer.start()
    consumer.awaitTermination()
  }
}

class CleanupConsumer(topic: String) extends TestConsumer(topic) {
  override protected def processMessage(key: String, value: String): Unit = {
    log.debug(s"Discarding message with key: $key.")
    super.processMessage(key, value)
  }
}
