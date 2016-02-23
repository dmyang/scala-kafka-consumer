package pd.kafkaconsumer.testsupport

import java.util.Properties
import org.apache.kafka.clients.producer.{ RecordMetadata, KafkaProducer, ProducerRecord }
import org.slf4j.LoggerFactory

class TestProducer(val topic: String) {
  protected val log = LoggerFactory.getLogger(this.getClass)
  protected lazy val producer = new KafkaProducer[String, String](producerProps)

  protected def producerProps(): Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("acks", "all")
    props.put("retries", 0: Integer)
    props.put("batch.size", 16384: Integer)
    props.put("linger.ms", 1: Integer)
    props.put("buffer.memory", 33554432: Integer)
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props
  }

  def send(key: String, message: String): Unit = {
    val future = producer.send(new ProducerRecord(topic, key, message))
    val result = future.get()
    log.debug(s"Sent message with key $key as ${result.topic}/${result.partition}/${result.offset}")
  }

  def shutdown(): Unit = {
    producer.close()
  }
}
