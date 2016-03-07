package pd.kafkaconsumer

import pd.dns.finder._
import org.slf4j.LoggerFactory

/**
 * Some helpers for Service lookups. This basically adds some convention on top of the
 * service-finder library. Note that values returned from lookup calls can never be
 * cached - for every connection, a fresh lookup must be done so that a client is always
 * using the latest cluster state.
 *
 * @param clusterName the Kafka cluster base name; the environment will be deduced automatically
 */
class KafkaClusterLookup(clusterName: String) {
  private val log = LoggerFactory.getLogger(this.getClass)
  private val tags = Map("production" -> "prod", "staging" -> "stg", "load_test" -> "lt")

  /**
   * Find the Kafka bootstrap server. This will return localhost:9292 for
   * development environments, and the kafka cluster with the correct
   * tag for other environments.
   * @return a hostname:port string pointing to a bootstrap node.
   */
  def findBootstrapServer: String = {

    val environment = KafkaClusterLookup.EnvironmentFinder.findEnvironment
    if (canLookupInEnvironment(environment)) {
      lookupHostPort(s"${tagFor(environment)}.kafka.service.consul")
    } else {
      "localhost:9092"
    }
  }

  private def canLookupInEnvironment(environment: String): Boolean = {
    !(environment == null || environment == "development")
  }

  private[kafkaconsumer] def tagFor(environment: String) =
    s"${tags.getOrElse(environment, environment)}-${clusterName}"

  private[kafkaconsumer] def lookupHostPort(
    lookupName: String, finder: ServiceFinder = serviceFinder()
  ): String = {
    val (host, port) = finder.find(lookupName)
    val hostPort = s"$host:$port"
    log.info(s"Service lookup: $lookupName => $hostPort")
    hostPort
  }
}

object KafkaClusterLookup {
  trait EnvironmentFinder {
    def findEnvironment: String
  }

  object EnvironmentFinder extends EnvironmentFinder {
    def findEnvironment = System.getenv("PD_ENV")
  }
}
