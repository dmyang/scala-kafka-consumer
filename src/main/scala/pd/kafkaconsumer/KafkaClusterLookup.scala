package pd.kafkaconsumer

import org.slf4j.LoggerFactory

/**
  * Some helpers for Service lookups. This basically adds some convention on top of the
  * service-finder library. Note that values returned from lookup calls can never be
  * cached - for every connection, a fresh lookup must be done so that a client is always
  * using the latest cluster state.
  */
object KafkaClusterLookup {
  private val log = LoggerFactory.getLogger(this.getClass)
  private val tags = Map("production" -> "prod", "staging" -> "stg")

  /**
    * Find the Kafka bootstrap server. This will return localhost:9292 for
    * development environments, and the kafka cluster with the correct
    * tag for other environments.
    * @return a hostname:port string pointing to a bootstrap node.
    */
  def findBootstrapServer: String = {

    val environment = System.getenv("PD_ENV")
    if (canLookupInEnvironment(environment)) {
      lookupHostPort(s"${tagFor(environment)}.kafka.service.consul")
    }
    else {
      "localhost:9092"
    }
  }

  def canLookupInEnvironment(environment: String): Boolean = {
    !(environment == null || environment == "development")
  }

  def tagFor(environment: String): String = s"${tags.getOrElse(environment, environment)}-datahose"

  def lookupHostPort(lookupName: String): String = {
    val finder = pd.dns.finder.serviceFinder()
    val (host, port) = finder.find(lookupName)
    val hostPort = s"$host:$port"
    log.info(s"Service lookup: $lookupName => $hostPort")
    hostPort
  }
}
