# PagerDuty Kafka Consumer [![Build Status](https://travis-ci.com/PagerDuty/pd-kafka-consumer.svg?token=7Mi8LhmhpJYhzs4euq1w&branch=master)](https://travis-ci.com/PagerDuty/pd-kafka-consumer/builds)

This is an open source project!

This is an opinionated wrapper around the Kafka consumer, adding a default set
of policies/interactions. It also specifies the version of the Kafka client
library to use as a transitive dependency (so don't double-specify it if
you're depending on this library) and provides a base test class for Kafka-eske
integration tests.

## Usage

Create a new `SimpleKafkaConsumer` and get cranking. Usually you need three items to get a default
consumer up and running:

- The topic the consumer works on (constructor argument)
- The consumer group your consumer belongs to (passed through properties)
- A bootstrap server to start talking to the Kafka cluster (passed through properties)

For testing, you can re-use/build on the classes in the `pd.kafkaconsumer.testsupport`
package, which contains a base Spec to inherit from if you need a Kafka integration
test.

## Building

Very basic sbt setup. For now, all tests are integration tests but run during the
regular "test" phase, so before doing `sbt test` make sure a Kafka and Zookeeper
instance are running on the local machine.

## Release

Follow these steps to release a new version:
 - Update version.sbt in your PR
 - Update CHANGELOG.md in your PR
 - When the PR is approved, merge it to master, and delete the branch
 - Travis will run all tests, publish to Artifactory, and create a new version tag in Github
