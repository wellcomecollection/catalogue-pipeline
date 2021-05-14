import sbt._

object WellcomeDependencies {

  val defaultVersion = "26.14.0"

  lazy val versions = new {
    val typesafe = defaultVersion
    val fixtures = defaultVersion
    val json = defaultVersion
    val messaging = defaultVersion
    val monitoring = defaultVersion
    val storage = defaultVersion
    val elasticsearch = defaultVersion
    val internalModel = "3814.f87f16965422e31758b8dc271798f79c1caa0b01"
  }

  val internalModel: Seq[ModuleID] = library(
    name = "internal_model",
    version = versions.internalModel
  )
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

  val elasticsearchLibrary: Seq[ModuleID] = library(
    name = "elasticsearch",
    version = versions.elasticsearch
  )

  val elasticsearchTypesafeLibrary: Seq[ModuleID] = library(
    name = "elasticsearch_typesafe",
    version = versions.elasticsearch
  )

  val monitoringLibrary: Seq[ModuleID] = library(
    name = "monitoring",
    version = versions.monitoring
  )

  val monitoringTypesafeLibrary: Seq[ModuleID] = monitoringLibrary ++ library(
    name = "monitoring_typesafe",
    version = versions.monitoring
  )

  val storageLibrary: Seq[ModuleID] = library(
    name = "storage",
    version = versions.storage
  )

  val typesafeLibrary: Seq[ModuleID] = library(
    name = "typesafe_app",
    version = versions.typesafe
  ) ++ fixturesLibrary

  val storageTypesafeLibrary: Seq[ModuleID] = storageLibrary ++ library(
    name = "storage_typesafe",
    version = versions.storage
  )

  val messagingTypesafeLibrary: Seq[ModuleID] = messagingLibrary ++ library(
    name = "messaging_typesafe",
    version = versions.messaging
  ) ++ monitoringLibrary

  private def library(name: String, version: String): Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% name % version,
    "uk.ac.wellcome" %% name % version % "test" classifier "tests"
  )
}

object ExternalDependencies {
  lazy val versions = new {
    val akka = "2.6.10"
    val akkaHttp = "10.1.11"
    val akkaHttpCirce = "1.32.0"
    val akkaStreamAlpakka = "1.1.2"
    val apacheCommons = "1.9"
    val circe = "0.13.0"
    val fastparse = "2.3.0"
    val mockito = "1.9.5"
    val scalatest = "3.2.3"
    val scalatestplus = "3.1.2.0"
    val scalatestplusMockitoArtifactId = "mockito-3-2"
    val scalacheckShapeless = "1.1.6"
    val scalacsv = "1.3.5"
    val scalaGraph = "1.12.5"
    val enumeratum = "1.6.1"
    val enumeratumScalacheck = "1.6.1"
    val jsoup = "1.13.1"
    val scalaJHttp = "2.3.0"
  }

  val enumeratumDependencies = Seq(
    "com.beachape" %% "enumeratum" % versions.enumeratum,
    "com.beachape" %% "enumeratum-scalacheck" % versions.enumeratumScalacheck % "test"
  )

  val scribeJavaDependencies = Seq(
    "com.github.dakatsuka" %% "akka-http-oauth2-client" % "0.2.0")

  val akkaHttpDependencies = Seq(
    "com.typesafe.akka" %% "akka-testkit" % versions.akka % "test",
    "com.typesafe.akka" %% "akka-http" % versions.akkaHttp,
    "com.typesafe.akka" %% "akka-http-testkit" % versions.akkaHttp % "test",
    "de.heikoseeberger" %% "akka-http-circe" % versions.akkaHttpCirce
  )

  val alpakkaS3Dependencies = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-s3" % versions.akkaStreamAlpakka
  )

  val apacheCommonsDependencies = Seq(
    "org.apache.commons" % "commons-text" % versions.apacheCommons
  )

  val circeOpticsDependencies = Seq(
    "io.circe" %% "circe-optics" % versions.circe
  )

  val mockitoDependencies: Seq[ModuleID] = Seq(
    "org.mockito" % "mockito-core" % versions.mockito % "test",
    "org.scalatestplus" %% versions.scalatestplusMockitoArtifactId % versions.scalatestplus % "test")

  val wireMockDependencies = Seq(
    "com.github.tomakehurst" % "wiremock" % "2.25.1" % Test
  )

  val mySqlDependencies = Seq(
    "org.flywaydb" % "flyway-core" % "4.2.0",
    "org.scalikejdbc" %% "scalikejdbc" % "3.4.0",
    "mysql" % "mysql-connector-java" % "6.0.6"
  )

  val scalacheckDependencies = Seq(
    "org.scalatestplus" %% "scalacheck-1-14" % versions.scalatestplus % "test",
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

  val parseDependencies = Seq(
    "com.lihaoyi" %% "fastparse" % versions.fastparse
  )

  val scalaXmlDependencies = Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  )

  val jsoupDependencies = Seq(
    "org.jsoup" % "jsoup" % versions.jsoup
  )

  val scalaJDependencies = Seq(
    "org.scalaj" %% "scalaj-http" % versions.scalaJHttp
  )
}

object CatalogueDependencies {
  val internalModelDependencies: Seq[ModuleID] =
    ExternalDependencies.scalacsvDependencies ++
      WellcomeDependencies.fixturesLibrary ++
      WellcomeDependencies.elasticsearchLibrary ++
      WellcomeDependencies.elasticsearchTypesafeLibrary ++
      WellcomeDependencies.jsonLibrary ++
      ExternalDependencies.parseDependencies ++
      ExternalDependencies.scalacheckDependencies ++
      ExternalDependencies.enumeratumDependencies ++
      ExternalDependencies.scalaXmlDependencies ++
      WellcomeDependencies.storageLibrary

  val flowDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val sourceModelDependencies: Seq[sbt.ModuleID] =
    WellcomeDependencies.storageLibrary ++
      WellcomeDependencies.fixturesLibrary ++
      ExternalDependencies.scalatestDependencies

  val sourceModelTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageTypesafeLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary

  val pipelineStorageDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingLibrary ++
      WellcomeDependencies.typesafeLibrary

  val pipelineStorageTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary

  val elasticsearchTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val transformerCommonDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageLibrary

  val idminterDependencies: Seq[ModuleID] =
    ExternalDependencies.mySqlDependencies ++
      ExternalDependencies.circeOpticsDependencies

  val matcherDependencies: Seq[ModuleID] =
    ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.scalaGraphDependencies ++
      WellcomeDependencies.storageTypesafeLibrary

  val mergerDependencies: Seq[ModuleID] = Nil

  val relationEmbedderDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageTypesafeLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary

  val routerDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary

  val batcherDependencies: Seq[ModuleID] =
    ExternalDependencies.scalatestDependencies ++
      WellcomeDependencies.typesafeLibrary ++
      WellcomeDependencies.fixturesLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary

  val miroTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      WellcomeDependencies.storageTypesafeLibrary

  val reindexWorkerDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageTypesafeLibrary ++
      WellcomeDependencies.fixturesLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.typesafeLibrary ++
      ExternalDependencies.scalatestDependencies

  val sierraTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      WellcomeDependencies.jsonLibrary

  val metsTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies

  val calmTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.jsoupDependencies ++
      ExternalDependencies.parseDependencies

  // METS adapter

  val metsAdapterDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies ++
      ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.mockitoDependencies ++
      ExternalDependencies.wireMockDependencies ++
      ExternalDependencies.scribeJavaDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.storageTypesafeLibrary

  // CALM adapter

  val calmApiClientDependencies: Seq[ModuleID] =
    ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.scalaXmlDependencies ++
      ExternalDependencies.scalatestDependencies

  // Sierra adapter stack

  val sierraLinkerDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val sierraMergerDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val sierraReaderDependencies: Seq[ModuleID] =
    ExternalDependencies.circeOpticsDependencies ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      ExternalDependencies.scalaJDependencies ++
      WellcomeDependencies.jsonLibrary ++
      WellcomeDependencies.typesafeLibrary

  // Inference manager
  val inferenceManagerDependencies: Seq[ModuleID] =
    ExternalDependencies.akkaHttpDependencies ++
      ExternalDependencies.wireMockDependencies

  // TEI adapter

  val teiGitHubDependencies: Seq[ModuleID] = {
    WellcomeDependencies.messagingTypesafeLibrary ++
      ExternalDependencies.scalatestDependencies
  }
}
