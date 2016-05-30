publishArtifact in Test := true
crossScalaVersions := Seq("2.10.6", "2.11.7")

lazy val root = (project in file(".")).
  configs(IntegrationTest).
  settings(Defaults.itSettings: _*).
  settings(
    organization := "com.pagerduty",
    name := "pd-kafka-consumer",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "0.9.0.1",
      "org.slf4j" % "slf4j-api" % "1.7.12",
      "org.slf4j" % "slf4j-simple" % "1.7.12" % "it,test",
      "org.scalactic" %% "scalactic" % "2.2.6" % "it,test",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "it,test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "it,test"))

resolvers ++= Seq(
  Resolver.url(
    "artifactory-pagerduty-ivy",
    url("https://pagerduty.artifactoryonline.com/pagerduty/all/"))(Resolver.ivyStylePatterns))
