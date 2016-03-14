[![Build Status](https://travis-ci.com/PagerDuty/pd-kafka-consumer.svg?token=7Mi8LhmhpJYhzs4euq1w&branch=master)](https://travis-ci.com/PagerDuty/pd-kafka-consumer)

# PagerDuty Kafka Consumer

This is an opinionated wrapper around the Kafka consumer, adding a default set
of policies/interactions. It also specifies the version of the Kafka client
library to use as a transitive dependency (so don't double-specify it if
you're depending on this library) and provides a base test class for Kafka-eske
integration tests.

## Usage

Create a new `[SimpleKafkaConsumer](https://docs.pd-internal.com/scala/pd-kafka-consumer/pd/kafkaconsumer/SimpleKafkaConsumer.html)` and get cranking. Usually you need three items to get a default
consumer up and running:

- The topic the consumer works on (constructor argument)
- The consumer group your consumer belongs to (passed through properties)
- A bootstrap server to start talking to the Kafka cluster (passed through properties)

By convention, the bootstrap server needs to be fetched using
`[KafkaClusterLookup](https://docs.pd-internal.com/scala/pd-kafka-consumer/pd/kafkaconsumer/KafkaClusterLookup.html).findBootstrapServer` - note that you're not allowed to cache
the value of the lookup, you want it fresh every time so you get up-to-date
information from the service discovery system.

For testing, you can re-use/build on the classes in the `pd.kafkaconsumer.testsupport`
package, which contains a base Spec to inherit from if you need a Kafka integration
test.

## Building

Very basic sbt setup. For now, all tests are integration tests but run during the
regular "test" phase, so before doing `sbt test` make sure a Kafka and Zookeeper
instance are running on the local machine.

## Ownership

This library is owned by the Core team. Hop on 
[#core-contemplations](https://pagerduty.slack.com/messages/core-contemplations/)
on Slack for help and more info.

