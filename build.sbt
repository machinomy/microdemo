name := "microdemo"

version := "1.0"

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "Sonatype" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "tomp2p.net" at "http://tomp2p.net/dev/mvn/"
)

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.bitcoinj" % "bitcoinj-core" % "0.13.5",
  "org.scodec" %% "scodec-core" % "1.9.0",
  "com.github.jnr" % "jnr-ffi" % "2.0.9",
  "com.typesafe.akka" %% "akka-actor" % "2.4.5",
  "com.typesafe.akka" %% "akka-http-core" % "2.4.5",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.5",
  "com.github.nscala-time" %% "nscala-time" % "2.10.0",
  "io.argonaut" %% "argonaut" % "6.1",
  "org.slf4j" % "slf4j-simple" % "1.7.21",
  "net.tomp2p" % "tomp2p-all" % "5.0-Beta8"
)
