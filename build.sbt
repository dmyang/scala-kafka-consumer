lazy val publishSettings = Seq(
  bintrayOrganization := Some("pagerduty"),
  bintrayRepository := "oss-maven",
  licenses += ("BSD New", url("https://opensource.org/licenses/BSD-3-Clause")),
  publishMavenStyle := true,
  pomExtra := (
    <url>https://github.com/PagerDuty/scala-kafka-consumer</url>
      <scm>
        <url>git@github.com:PagerDuty/scala-kafka-consumer.git</url>
        <connection>scm:git:git@github.com:PagerDuty/scala-kafka-consumer.git</connection>
      </scm>
      <developers>
        <developer>
          <id>lexn82</id>
          <name>Aleksey Nikiforov</name>
          <url>https://github.com/lexn82</url>
        </developer>
        <developer>
          <id>cdegroot</id>
          <name>Cees de Groot</name>
          <url>https://github.com/cdegroot</url>
        </developer>
        <developer>
          <id>DWvanGeest</id>
          <name>David van Geest</name>
          <url>https://github.com/DWvanGeest</url>
        </developer>
        <developer>
          <id>divtxt</id>
          <name>Div Shekhar</name>
          <url>https://github.com/divtxt</url>
        </developer>
        <developer>
          <id>jppierri</id>
          <name>Joseph Pierri</name>
          <url>https://github.com/jppierri</url>
        </developer>
      </developers>)
)

lazy val sharedSettings = Seq(
  organization := "com.pagerduty",
  scalaVersion := "2.11.11",
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
    publishLocal := {},
    publish := {},
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "0.10.1.1",
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
      "org.apache.kafka" % "kafka-clients" % "0.10.1.1",
      "org.slf4j" % "slf4j-api" % "1.7.12")
  )

lazy val partitioned = (project in file("partitioned")).
  settings(publishSettings: _*).
  settings(
    name := "kafka-consumer-partitioned",
    organization := "com.pagerduty",
    scalaVersion := "2.11.11",
    crossScalaVersions := Seq("2.11.11", "2.12.2"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.3",
      "com.typesafe.akka" %% "akka-stream-kafka" % "0.16",
      "org.slf4j" % "slf4j-api" % "1.7+",
      "com.typesafe.akka" %% "akka-slf4j" % "2.4.18"
    )
  )

lazy val root = Project(
  id = "root",
  base = file("."),
  aggregate = Seq(tests, testSupport, main, partitioned),
  settings = Project.defaultSettings ++ Seq(
    publishLocal := {},
    publish := {},
    publishArtifact := false)
  )
