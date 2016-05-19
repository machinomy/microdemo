name := "microdemo"

version := "1.0"

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "Tox4j" at "http://tox4j.github.io/repositories/snapshots",
  "Sonatype" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.bitcoinj" % "bitcoinj-core" % "0.13.5",
  "im.tox" %% "tox4j" % "0.1-SNAPSHOT",
  "codes.reactive" %% "scala-time" % "0.3.0-SNAPSHOT"
)
