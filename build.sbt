publishArtifact in Test := true

lazy val commonSettings = Seq(
  organization := "com.pagerduty",
  version := "0.1.0",
  scalaVersion := "2.10.6"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "pd-kafka-consumer",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "0.9.0.0",
      "org.slf4j" % "slf4j-api" % "1.7.12",
      "com.pagerduty" %% "service-finder" % "1.0.1",
      "org.scalactic" %% "scalactic" % "2.2.6",
      "org.scalatest" %% "scalatest" % "2.2.6"))
