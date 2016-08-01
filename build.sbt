lazy val publishSettings = Seq(
  bintrayOrganization := Some("pagerduty"),
  bintrayRepository := "oss-maven",
  licenses += ("BSD New", url("https://opensource.org/licenses/BSD-3-Clause")),
  publishMavenStyle := true,
  pomExtra := (
    <url>https://github.com/PagerDuty/kafka-consumer</url>
      <scm>
        <url>git@github.com:PagerDuty/kafka-consumer.git</url>
        <connection>scm:git:git@github.com:PagerDuty/kafka-consumer.git</connection>
      </scm>
      <developers>
        <developer>
          <id>divtxt</id>
          <name>Div Shekhar</name>
          <url>https://github.com/divtxt</url>
        </developer>
      </developers>)
)

lazy val sharedSettings = Seq(
  organization := "com.pagerduty",
  scalaVersion := "2.10.6",
  crossScalaVersions := Seq("2.10.6", "2.11.7")
)

lazy val tests = (project in file("tests")).
  configs(IntegrationTest).
  dependsOn(main, testSupport).
  settings(Defaults.itSettings: _*).
  settings(sharedSettings: _*).
  settings(
    name := "kafka-consumer-tests",
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
    name := "kafka-consumer-test-support",
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
    name := "kafka-consumer",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "0.9.0.1",
      "org.slf4j" % "slf4j-api" % "1.7.12")
  )
