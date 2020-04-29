name := """oauth20-provider"""

version := "1.0"

scalaVersion := "2.12.10"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

scalacOptions := Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:reflectiveCalls",
  "-encoding", "utf8",
  "-target:jvm-1.8"
)

val AkkaHttpV = "10.1.11"
val AkkaV = "2.6.3"
val circeVersion = "0.13.0"
val slickPgVersion = "0.19.0"

val postgresDeps = Seq(
  "com.typesafe.slick" %% "slick" % "3.3.2",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
  "org.liquibase" % "liquibase-core" % "3.8.9",
  "com.mattbertolini" % "liquibase-slf4j" % "2.0.0",
  "org.yaml" % "snakeyaml" % "1.25",
  "com.github.tminglei" %% "slick-pg" % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_circe-json" % slickPgVersion
)

libraryDependencies ++= postgresDeps ++ Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaV,
//  "com.typesafe.akka" %% "akka-actor" % AkkaV,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaV,
//  "com.typesafe.akka" %% "akka-stream-typed" % AkkaV,
//  "com.typesafe.akka" %% "akka-stream" % AkkaV,

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaV % "test",

  "com.typesafe.akka" %% "akka-http-core" % AkkaHttpV,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpV,
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpV,

  "org.scaldi" %% "scaldi" % "0.5.8",
  "org.scaldi" %% "scaldi-akka" % "0.5.8",

  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "de.heikoseeberger" %% "akka-http-circe" % "1.31.0",

//  "com.github.scopt" %% "scopt" % "4.0.0-RC2",
  
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",

  "org.codehaus.groovy" % "groovy-all" % "2.5.8" % "test",
  "org.spockframework" % "spock-core" % "1.3-groovy-2.5" % "test",
  "org.spockframework" % "spock-guice" % "1.3-groovy-2.5" % "test",

  "org.typelevel" %% "cats-core" % "2.1.1",

  "commons-codec" % "commons-codec" % "1.13",

  "io.circe" %% "circe-yaml" % circeVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion
)

assemblyJarName in assembly := "oauth20-provider.jar"
mainClass in assembly := Some("com.fan.impactech.Oauth20ProviderApp")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}