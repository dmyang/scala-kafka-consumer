# PagerDuty Kafka Consumer

[![Build Status](https://travis-ci.org/PagerDuty/scala-kafka-consumer.svg?branch=master)](https://travis-ci.org/PagerDuty/scala-kafka-consumer)

This is an open source project!

This is an opinionated wrapper around the Kafka consumer, adding a default set
of policies/interactions. It also specifies the version of the Kafka client
library to use as a transitive dependency (so don't double-specify it if
you're depending on this library) and provides a base test class for Kafka-eske
integration tests.

*** NOTE: This library uses Java 8 functionality. ***

## Installation

This library is published as a number of different artifacts to the PagerDuty Bintray OSS Maven repository:

```scala
resolvers += "bintray-pagerduty-oss-maven" at "https://dl.bintray.com/pagerduty/oss-maven"
```

Currently, there are two different consumers available:

 - [`SimpleKafkaConsumer`](main/src/main/scala/com/pagerduty/kafkaconsumer/SimpleKafkaConsumer.scala) - As the name suggests, 
   this consumer is very simple to use and has few dependencies. It is available as follows:
   
   ```scala
   libraryDependencies += "com.pagerduty" %% "kafka-consumer" % "<version>"
   ```   
   
 - [`PartitionedKafkaConsumer`](partitioned/src/main/scala/com/pagerduty/kafkaconsumer/PartitionedKafkaConsumer.scala) - 
   This consumer improves on `SimpleKafkaConsumer` at the expense of an additional dependency
   (Akka Streams) and no support for 2.10. This consumer processes and commits Kafka messages for each assigned partition
   independently and in parallel. Therefore, this consumer is both faster and more tolerant of transient processing failures.
   It is available as follows:
   
   ```scala
   libraryDependencies += "com.pagerduty" %% "kafka-consumer-partitioned" % "<version>"
   ```   

## Usage

This library is published as a number of separate artifacts

Create a new `SimpleKafkaConsumer` or `PartitionedKafkaConsumer` and get cranking. Usually you need three items to get a default
consumer up and running:

- The topic the consumer works on (constructor argument)
- The consumer group your consumer belongs to (passed through properties)
- A bootstrap server to start talking to the Kafka cluster (passed through properties)

For testing, you can re-use/build on the classes in the `com.pagerduty.kafkaconsumer.testsupport`
package, which contains a base Spec to inherit from if you need a Kafka integration
test.

## Release

Follow these steps to release a new version:
 - Update version.sbt in your PR
 - Update CHANGELOG.md in your PR
 - When the PR is approved, merge it to master, and delete the branch
 - Travis will run all tests, publish to Artifactory, and create a new version tag in Github
