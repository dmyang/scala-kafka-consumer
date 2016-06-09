lazy val publishSettings = Seq(
  resolvers := Seq(
    "artifactory-pagerduty-maven" at "https://pagerduty.artifactoryonline.com/pagerduty/all/",
    Resolver.url( // Add Artifactory again, this time treating it as an ivy repository
      "artifactory-pagerduty-ivy",
      url("https://pagerduty.artifactoryonline.com/pagerduty/all/")
    )(Resolver.ivyStylePatterns),
    Resolver.defaultLocal),
    publishTo := Some(
    Resolver.url(
      "artifactory-pagerduty-releases",
      url("https://pagerduty.artifactoryonline.com/pagerduty/pd-releases/")
    )(Resolver.ivyStylePatterns)),
    publishMavenStyle := false
)

lazy val sharedSettings = Seq(
  organization := "com.pagerduty",
  version := "0.2.6",
  scalaVersion := "2.10.6",
  crossScalaVersions := Seq("2.10.6", "2.11.7")
)

lazy val tests = (project in file("tests")).
  configs(IntegrationTest).
  dependsOn(main, testSupport).
  settings(Defaults.itSettings: _*).
  settings(sharedSettings: _*).
  settings(
    name := "pd-kafka-consumer-tests",
    publishArtifact in Compile := false,
    publishArtifact in Test := false,
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "0.9.0.1",
      "org.scalactic" %% "scalactic" % "2.2.6" % "it,test",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "it,test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "it,test",
      "org.slf4j" % "slf4j-api" % "1.7.12",
      "org.slf4j" % "slf4j-simple" % "1.7.12" % "it,test")
  )

lazy val testSupport = (project in file("test-support")).
  dependsOn(main).
  settings(sharedSettings: _*).
  settings(publishSettings: _*).
  settings(
    name := "pd-kafka-consumer-test-support",
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "2.2.6",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2",
      "org.scalatest" %% "scalatest" % "2.2.6",
      "org.slf4j" % "slf4j-simple" % "1.7.12")
  )

lazy val main = (project in file("main")).
  settings(sharedSettings: _*).
  settings(publishSettings: _*).
  settings(
    name := "pd-kafka-consumer",
    libraryDependencies ++= Seq(
      "com.pagerduty" %% "pd-stats" % "1.0.0",
      "org.apache.kafka" % "kafka-clients" % "0.9.0.1",
      "org.slf4j" % "slf4j-api" % "1.7.12")
  )
