# PagerDuty Kafka Consumer

This is an opinionated wrapper around the Kafka consumer, adding a default set
of policies/interactions. It also specifies the version of the Kafka client
library to use as a transitive dependency (so don't double-specify it if
you're depending on this library).

