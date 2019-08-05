import sbt._

object WellcomeDependencies {
  lazy val versions = new {
    val fixtures = "1.0.0"
    val json = "1.1.1"
    val messaging = "1.6.0"
    val monitoring = "2.0.0"
    val storage = "3.6.0"
    val typesafe = "1.0.0"

    val newMessaging = "5.3.1"
    val newMonitoring = "2.3.0"
    val newStorage = "6.1.3"

    val sierraStreamsSource = "0.4"
  }

  val jsonLibrary: Seq[ModuleID] = library(
    name = "json",
    version = versions.json
  )

  val fixturesLibrary: Seq[ModuleID] = library(
    name = "fixtures",
    version = versions.fixtures
  )

  val messagingLibrary: Seq[ModuleID] = library(
    name = "messaging",
    version = versions.messaging
  )

  val newMessagingLibrary: Seq[ModuleID] = library(
    name = "messaging",
    version = versions.newMessaging
  )

  val monitoringLibrary: Seq[ModuleID] = library(
    name = "monitoring",
    version = versions.monitoring
  )

  val newMonitoringLibrary: Seq[ModuleID] = library(
    name = "monitoring",
    version = versions.newMonitoring
  )

  val storageLibrary: Seq[ModuleID] = library(
    name = "storage",
    version = versions.storage
  )

  val newStorageLibrary: Seq[ModuleID] = library(
    name = "storage",
    version = versions.newStorage
  )

  val typesafeLibrary: Seq[ModuleID] = library(
    name = "typesafe-app",
    version = versions.typesafe
  ) ++ fixturesLibrary

  val newStorageTypesafeLibrary: Seq[ModuleID] = newStorageLibrary ++ library(
    name = "storage_typesafe",
    version = versions.newStorage
  )

  val newMessagingTypesafeLibrary
    : Seq[ModuleID] = newMessagingLibrary ++ library(
    name = "messaging_typesafe",
    version = versions.newMessaging
  ) ++ newMonitoringLibrary

  val messagingTypesafeLibrary: Seq[ModuleID] = messagingLibrary ++ library(
    name = "messaging_typesafe",
    version = versions.messaging
  ) ++ monitoringLibrary ++ storageLibrary ++ typesafeLibrary

  val sierraStreamsSourceLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% "sierra-streams-source" % versions.sierraStreamsSource
  )

  private def library(name: String, version: String): Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% name % version,
    "uk.ac.wellcome" %% name % version % "test" classifier "tests"
  )
}

object ExternalDependencies {
  lazy val versions = new {
    val akka = "2.5.9"
    val akkaStreamAlpakka = "0.20"
    val apacheCommons = "3.7"
    val apacheLogging = "2.8.2"
    val aws = "1.11.95"
    val circe = "0.9.0"
    val elastic4s = "7.1.0"
    val fastparse = "2.1.3"
    val finatra = "18.11.0"
    val guice = "4.2.0"
    val logback = "1.2.3"
    val mockito = "1.9.5"
    val scalacheck = "1.13.4"
    val scalacheckShapeless = "1.1.6"
    val scalacsv = "1.3.5"
    val scalaGraph = "1.12.5"
    val scalatest = "3.0.1"
    val logstashLogback ="6.1"
  }

  val logbackDependencies = Seq(
    "ch.qos.logback" % "logback-classic" % versions.logback,
    "ch.qos.logback" % "logback-core" % versions.logback,
    "ch.qos.logback" % "logback-access" % versions.logback,

    "net.logstash.logback" % "logstash-logback-encoder" % versions.logstashLogback
  )

  val akkaActorDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % versions.akka,
  )

  val alpakkaS3Dependencies = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-s3" % versions.akkaStreamAlpakka
  )

  val apacheCommonsDependencies = Seq(
    "org.apache.commons" % "commons-lang3" % versions.apacheCommons
  )

  val circeOpticsDependencies = Seq(
    "io.circe" %% "circe-optics" % versions.circe
  )

  val elasticsearchDependencies = Seq(
    "org.apache.logging.log4j" % "log4j-core" % versions.apacheLogging,
    "org.apache.logging.log4j" % "log4j-api" % versions.apacheLogging,
    "com.sksamuel.elastic4s" %% "elastic4s-core" % versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-http-streams" % versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-testkit" % versions.elastic4s % "test"
  )

  val finatraDependencies = Seq(
    "com.twitter" %% "finatra-http" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "finatra-http" % versions.finatra,
    "com.twitter" %% "finatra-httpclient" % versions.finatra,
    "com.twitter" %% "finatra-jackson" % versions.finatra % "test",
    "com.twitter" %% "finatra-jackson" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-app" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-app" % versions.finatra % "test",
    "com.twitter" %% "inject-core" % versions.finatra,
    "com.twitter" %% "inject-core" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-modules" % versions.finatra % "test",
    "com.twitter" %% "inject-modules" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-server" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-server" % versions.finatra % "test"
  )

  val guiceDependencies = Seq(
    "com.google.inject" % "guice" % versions.guice,
    "com.google.inject.extensions" % "guice-testlib" % versions.guice % "test"
  )

  val mockitoDependencies: Seq[ModuleID] = Seq(
    "org.mockito" % "mockito-core" % versions.mockito % "test"
  )

  val mySqlDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk-rds" % versions.aws,
    "org.flywaydb" % "flyway-core" % "4.2.0",
    "org.scalikejdbc" %% "scalikejdbc" % "3.0.0",
    "mysql" % "mysql-connector-java" % "6.0.6"
  )

  val scalacheckDependencies = Seq(
    "org.scalacheck" %% "scalacheck" % versions.scalacheck % "test",
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % versions.scalacheckShapeless % "test"
  )

  val scalacsvDependencies = Seq(
    "com.github.tototoshi" %% "scala-csv" % versions.scalacsv
  )

  val scalaGraphDependencies = Seq(
    "org.scala-graph" %% "graph-core" % versions.scalaGraph
  )

  val scalatestDependencies = Seq(
    "org.scalatest" %% "scalatest" % versions.scalatest % "test"
  )

  val swaggerDependencies = Seq(
    "com.jakehschwartz" %% "finatra-swagger" % versions.finatra
  )

  val parseDependencies = Seq(
    "com.lihaoyi" %% "fastparse" % versions.fastparse
  )
}

object CatalogueDependencies {
  val commmonDependencies =
    ExternalDependencies.scalatestDependencies ++
      ExternalDependencies.logbackDependencies

  val internalModelDependencies =
    ExternalDependencies.scalacsvDependencies ++
      WellcomeDependencies.jsonLibrary ++
      ExternalDependencies.parseDependencies

  val displayModelDependencies =
    ExternalDependencies.swaggerDependencies ++
      ExternalDependencies.guiceDependencies ++
      ExternalDependencies.scalacheckDependencies

  val elasticsearchDependencies: Seq[ModuleID] =
    ExternalDependencies.elasticsearchDependencies ++
      ExternalDependencies.scalacheckDependencies ++
      WellcomeDependencies.fixturesLibrary

  val bigMessagingDependencies: Seq[ModuleID] =
    ExternalDependencies.scalatestDependencies ++
      WellcomeDependencies.typesafeLibrary ++
      WellcomeDependencies.newMonitoringLibrary ++
      WellcomeDependencies.newMessagingLibrary ++
      WellcomeDependencies.newStorageLibrary ++
      WellcomeDependencies.fixturesLibrary

  val bigMessagingTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.newStorageTypesafeLibrary ++
      WellcomeDependencies.newMessagingTypesafeLibrary

  val elasticsearchTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val apiDependencies: Seq[ModuleID] =
    ExternalDependencies.akkaActorDependencies ++
      ExternalDependencies.finatraDependencies ++
      ExternalDependencies.guiceDependencies ++
      WellcomeDependencies.fixturesLibrary

  val goobiReaderDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.jsonLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary

  val idminterDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.mySqlDependencies ++
      ExternalDependencies.circeOpticsDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val ingestorDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val matcherDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.scalaGraphDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val mergerDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val miroTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val recorderDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val reindexWorkerDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val sierraTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  // Sierra adapter stack

  val sierraAdapterCommonDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val sierraReaderDependencies: Seq[ModuleID] =
    WellcomeDependencies.sierraStreamsSourceLibrary ++
      ExternalDependencies.circeOpticsDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  // Snapshots stack

  val snapshotGeneratorDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      ExternalDependencies.alpakkaS3Dependencies
}
