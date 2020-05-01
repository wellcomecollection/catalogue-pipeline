import sbt._

object WellcomeDependencies {
  lazy val versions = new {
    val fixtures = "1.2.0"
    val json = "2.1.0"
    val messaging = "1.6.0"
    val monitoring = "2.0.0"
    val storage = "3.6.0"
    val typesafe = "2.0.0"

    val newMessaging = "9.1.0"
    val newMonitoring = "4.0.0"
    val newStorage = "8.1.0"

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
    val akka = "2.6.4"
    val akkaHttp = "10.1.11"
    val akkaHttpCirce = "1.32.0"
    val akkaStreamAlpakka = "1.1.2"
    val apacheCommons = "3.7"
    val apacheLogging = "2.8.2"
    val aws = "1.11.504"
    val circe = "0.13.0"
    val elastic4s = "7.6.1"
    val fastparse = "2.1.3"
    val swagger = "2.0.10"
    val logback = "1.2.3"
    val mockito = "1.9.5"
    val scalatestplusMockito = "3.1.0.0"
    val scalacheckShapeless = "1.1.6"
    val scalacsv = "1.3.5"
    val scalaGraph = "1.12.5"
    val scalatest = "3.1.1"
    val logstashLogback = "6.1"
    val scribeJava = "6.8.1"
    val apm = "1.12.0"
    val enumeratum = "1.5.13"
    val enumeratumScalacheck = "1.5.16"
  }
  val enumeratumDependencies = Seq(
    "com.beachape" %% "enumeratum" % versions.enumeratum,
    "com.beachape" %% "enumeratum-scalacheck" % versions.enumeratumScalacheck % "test"
  )

  val scribeJavaDependencies = Seq(
    "com.github.dakatsuka" %% "akka-http-oauth2-client" % "0.2.0")

  val logbackDependencies = Seq(
    "ch.qos.logback" % "logback-classic" % versions.logback,
    "ch.qos.logback" % "logback-core" % versions.logback,
    "ch.qos.logback" % "logback-access" % versions.logback,
    "net.logstash.logback" % "logstash-logback-encoder" % versions.logstashLogback
  )

  val apmDependencies = Seq(
    "co.elastic.apm" % "apm-agent-attach" % versions.apm,
    "co.elastic.apm" % "apm-agent-api" % versions.apm
  )

  val akkaHttpDependencies = Seq(
    "com.typesafe.akka" %% "akka-testkit" % versions.akka % "test",
    "com.typesafe.akka" %% "akka-http" % versions.akkaHttp,
    "com.typesafe.akka" %% "akka-http-testkit" % versions.akkaHttp % "test",
    "de.heikoseeberger" %% "akka-http-circe" % versions.akkaHttpCirce
  )

  val alpakkaS3Dependencies = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-s3" % versions.akkaStreamAlpakka
  )

  val alpakkaSnsDependencies = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-sns" % versions.akkaStreamAlpakka
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
    // This is our version of elastic4s.
    // Temporarily use it until until https://github.com/sksamuel/elastic4s/pull/2049
    // gets merged and released.
    // Also, the new version of elastic4s brings in scalatest_3.1.0 which is not backwards
    // compatible with scalatest_3.0.1 used here and a newer versio  of akka.
    // Both need to be updated in the wellcome libraries before being updated here,
    // so temporarily excluding those form the dependencies
    "uk.ac.wellcome.elastic4s" %% "elastic4s-core" % versions.elastic4s exclude("org.scalatest", "scalatest_2.12") excludeAll(ExclusionRule("com.typesafe.akka")),
    "uk.ac.wellcome.elastic4s" %% "elastic4s-client-esjava" % versions.elastic4s exclude("org.scalatest", "scalatest_2.12") excludeAll(ExclusionRule("com.typesafe.akka")),
    "uk.ac.wellcome.elastic4s" %% "elastic4s-http-streams" % versions.elastic4s exclude("org.scalatest", "scalatest_2.12") excludeAll(ExclusionRule("com.typesafe.akka")),
    "uk.ac.wellcome.elastic4s" %% "elastic4s-testkit" % versions.elastic4s % "test" exclude("org.scalatest", "scalatest_2.12") excludeAll(ExclusionRule("com.typesafe.akka"))
  )

  val mockitoDependencies: Seq[ModuleID] = Seq(
    "org.mockito" % "mockito-core" % versions.mockito % "test",
    "org.scalatestplus" %% "mockito-1-10" % versions.scalatestplusMockito % "test" )

  val wireMockDependencies = Seq(
    "com.github.tomakehurst" % "wiremock" % "2.25.1" % Test
  )

  val mySqlDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk-rds" % versions.aws,
    "org.flywaydb" % "flyway-core" % "4.2.0",
    "org.scalikejdbc" %% "scalikejdbc" % "3.4.0",
    "mysql" % "mysql-connector-java" % "6.0.6"
  )

  val awsSTSDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk-sts" % versions.aws
  )

  val scalacheckDependencies = Seq(
    "org.scalatestplus" %% "scalacheck-1-14" % "3.1.1.1" % "test",
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
    "io.swagger.core.v3" % "swagger-core" % versions.swagger,
    "io.swagger.core.v3" % "swagger-annotations" % versions.swagger,
    "io.swagger.core.v3" % "swagger-models" % versions.swagger,
    "io.swagger.core.v3" % "swagger-integration" % versions.swagger,
    "io.swagger.core.v3" % "swagger-jaxrs2" % versions.swagger,
    "javax.ws.rs" % "javax.ws.rs-api" % "2.0.1",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.8"
  )

  val parseDependencies = Seq(
    "com.lihaoyi" %% "fastparse" % versions.fastparse
  )

  val javaxDependencies = Seq(
    "javax.xml.bind" % "jaxb-api" % "2.3.0",
    "com.sun.xml.bind" % "jaxb-ri" % "2.3.0"
  )

  val scalaXmlDependencies = Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  )
}

object CatalogueDependencies {
  val commonDependencies =
    ExternalDependencies.scalatestDependencies ++
      ExternalDependencies.logbackDependencies ++
      ExternalDependencies.javaxDependencies

  val internalModelDependencies =
    ExternalDependencies.scalacsvDependencies ++
      WellcomeDependencies.jsonLibrary ++
      ExternalDependencies.parseDependencies ++
      ExternalDependencies.scalacheckDependencies ++
      ExternalDependencies.enumeratumDependencies

  val displayModelDependencies =
    ExternalDependencies.swaggerDependencies ++
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
    ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.apmDependencies ++
      ExternalDependencies.circeOpticsDependencies ++
      WellcomeDependencies.typesafeLibrary

  val idminterDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.mySqlDependencies ++
      ExternalDependencies.circeOpticsDependencies

  val ingestorDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies

  val matcherDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.scalaGraphDependencies

  val mergerDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies

  val miroTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.mockitoDependencies

  val recorderDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies

  val reindexWorkerDependencies: Seq[ModuleID] = Nil

  val sierraTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.mockitoDependencies

  val metsTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.wireMockDependencies ++
      ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.scribeJavaDependencies ++
      ExternalDependencies.scalaXmlDependencies ++
      ExternalDependencies.awsSTSDependencies ++
      WellcomeDependencies.typesafeLibrary

  val calmTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.mockitoDependencies

  // METS adapter

  val metsAdapterDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.wireMockDependencies ++
      ExternalDependencies.scribeJavaDependencies

  // CALM adapter

  val calmAdapterDependencies: Seq[ModuleID] =
    ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.scalaXmlDependencies

  // Sierra adapter stack

  val sierraAdapterCommonDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  val sierraReaderDependencies: Seq[ModuleID] =
    WellcomeDependencies.sierraStreamsSourceLibrary ++
      ExternalDependencies.circeOpticsDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary

  // Inference  manager
  val inferenceManagerDependencies: Seq[ModuleID] =
    ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.wireMockDependencies

  // Snapshots stack

  val snapshotGeneratorDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      WellcomeDependencies.newMessagingTypesafeLibrary ++
      WellcomeDependencies.newStorageLibrary ++
      ExternalDependencies.alpakkaS3Dependencies
}
