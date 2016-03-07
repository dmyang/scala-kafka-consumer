package pd.kafkaconsumer

import org.scalatest.{ FreeSpec, Matchers, path }
import org.scalamock.scalatest.MockFactory

class KafkaClusterLookupSpec extends FreeSpec with Matchers with MockFactory {
  "KafkaClusterLookup should" - {
    "use localhost in a testing or development environment" in {
      val lookup = new KafkaClusterLookup("myCluster")
      val envFinder = mock[KafkaClusterLookup.EnvironmentFinder]
      (envFinder.findEnvironment _).expects().returns("development")
      lookup.findBootstrapServer(envFinder) shouldBe "localhost:9092"
    }

    "find an appropriate host for a cluster name in a non-test environment" in {
      val env = "load_test"
      val clusterName = "clustercluster"
      val expectedTag = s"lt-${clusterName}"
      val lookup = new KafkaClusterLookup(clusterName)
      lookup.tagFor(env) shouldBe expectedTag

      val serviceFinder = mock[pd.dns.finder.ServiceFinder]
      (serviceFinder.find _).expects(expectedTag).returns(("10.10.10.10", 1234))
      lookup.lookupHostPort(expectedTag, serviceFinder) shouldBe "10.10.10.10:1234"
    }
  }
}
