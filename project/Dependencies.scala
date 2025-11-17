import sbt._
import scala.language.reflectiveCalls
object WellcomeDependencies {
  lazy val defaultVersion = "32.43.5"
  lazy val versions = new {
    val typesafe = defaultVersion
    val fixtures = defaultVersion
    val http = defaultVersion
    val json = defaultVersion
    val messaging = defaultVersion
    val monitoring = defaultVersion
    val storage = defaultVersion
    val elasticsearch = defaultVersion
    val sierra = defaultVersion
  }

  val jsonLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "json" % versions.json,
    "org.wellcomecollection" %% "json" % versions.json % "test" classifier "tests"
  )

  val fixturesLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "fixtures" % versions.fixtures,
    "org.wellcomecollection" %% "fixtures" % versions.fixtures % "test" classifier "tests"
  )

  val messagingLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "messaging" % versions.messaging,
    "org.wellcomecollection" %% "messaging" % versions.messaging % "test" classifier "tests"
  )

  val elasticsearchLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "elasticsearch" % versions.elasticsearch,
    "org.wellcomecollection" %% "elasticsearch" % versions.elasticsearch % "test" classifier "tests"
  )

  val elasticsearchTypesafeLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "elasticsearch_typesafe" % versions.elasticsearch,
    "org.wellcomecollection" %% "elasticsearch_typesafe" % versions.elasticsearch % "test" classifier "tests"
  )

  val httpLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "http" % versions.http,
    "org.wellcomecollection" %% "http" % versions.http % "test" classifier "tests"
  )

  val monitoringLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "monitoring" % versions.monitoring,
    "org.wellcomecollection" %% "monitoring" % versions.monitoring % "test" classifier "tests"
  )

  val monitoringTypesafeLibrary: Seq[ModuleID] = monitoringLibrary ++ Seq(
    "org.wellcomecollection" %% "monitoring_typesafe" % versions.monitoring,
    "org.wellcomecollection" %% "monitoring_typesafe" % versions.monitoring % "test" classifier "tests"
  )

  val storageLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "storage" % versions.storage,
    "org.wellcomecollection" %% "storage" % versions.storage % "test" classifier "tests"
  )

  val typesafeLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "typesafe_app" % versions.typesafe,
    "org.wellcomecollection" %% "typesafe_app" % versions.typesafe % "test" classifier "tests"
  ) ++ fixturesLibrary

  val storageTypesafeLibrary: Seq[ModuleID] = storageLibrary ++ Seq(
    "org.wellcomecollection" %% "storage_typesafe" % versions.storage,
    "org.wellcomecollection" %% "storage_typesafe" % versions.storage % "test" classifier "tests"
  )

  val messagingTypesafeLibrary: Seq[ModuleID] = messagingLibrary ++ Seq(
    "org.wellcomecollection" %% "messaging_typesafe" % versions.messaging,
    "org.wellcomecollection" %% "messaging_typesafe" % versions.messaging % "test" classifier "tests"
  ) ++ monitoringLibrary

  val sierraLibrary: Seq[ModuleID] = Seq(
    "org.wellcomecollection" %% "sierra" % versions.sierra,
    "org.wellcomecollection" %% "sierra" % versions.sierra % "test" classifier "tests"
  )

  val sierraTypesafeLibrary: Seq[ModuleID] = sierraLibrary ++ Seq(
    "org.wellcomecollection" %% "sierra_typesafe" % versions.sierra,
    "org.wellcomecollection" %% "sierra_typesafe" % versions.sierra % "test" classifier "tests"
  )
}

object ExternalDependencies {
  lazy val versions = new {
    val apacheCommons = "1.12.0"
    val awsLambdaClient = "2.6.0"
    val awsLambdaEvents = "3.15.0"
    val awsSSO = "2.31.54"
    val circe = "0.14.1"
    val diffJson = "4.1.1"
    val fastparse = "2.3.3"
    val scalatest = "3.2.19"
    val scalatestplus = "3.1.4.0"
    val scalacheckShapeless = "1.1.8"
    val scalacsv = "1.3.10"
    val scalaGraph = "1.13.1"
    val enumeratum = "1.7.4"
    val enumeratumScalacheck = "1.7.4"
    val jsoup = "1.18.1"
    val logback = "1.5.8"
    val scalatestPlus = "3.2.12.0"
    val scalatestPlusMockitoArtifactId = "mockito-4-5"
    val uPickle = "3.3.1"
  }

  val enumeratumDependencies = Seq(
    "com.beachape" %% "enumeratum" % versions.enumeratum,
    "com.beachape" %% "enumeratum-scalacheck" % versions.enumeratumScalacheck % "test"
  )

  val apacheCommonsDependencies = Seq(
    "org.apache.commons" % "commons-text" % versions.apacheCommons
  )

  val awsLambdaClient: Seq[ModuleID] = Seq(
    "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % versions.awsLambdaClient
  )
  val awsLambdaEvents = Seq(
    "com.amazonaws" % "aws-lambda-java-events" % versions.awsLambdaEvents
  )

  val uPickle = Seq(
    "com.lihaoyi" %% "upickle" % versions.uPickle
  )

  val circeOpticsDependencies = Seq(
    "io.circe" %% "circe-optics" % versions.circe
  )

  val mySqlDependencies = Seq(
    // Version 7.15.0 is the latest version of Flyway which supports MySQL 5.7. To update to a newer version of Flyway,
    // we would also need to update the MySQL tei-adapter-cluster and identifiers-delta-cluster databases in RDS.
    "org.flywaydb" % "flyway-core" % "7.15.0",
    "org.scalikejdbc" %% "scalikejdbc" % "3.4.2",
    "com.mysql" % "mysql-connector-j" % "9.3.0"
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
  val awsSSODependencies = Seq(
    "software.amazon.awssdk" % "sso" % versions.awsSSO,
    "software.amazon.awssdk" % "sts" % versions.awsSSO
  )
  val scalatestDependencies = Seq(
    "org.scalatestplus" %% versions.scalatestPlusMockitoArtifactId % versions.scalatestPlus % Test,
    "org.scalatest" %% "scalatest" % versions.scalatest % "test"
  )

  val parseDependencies = Seq(
    "com.lihaoyi" %% "fastparse" % versions.fastparse
  )

  val scalaXmlDependencies = Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.3.1"
  )

  val jsoupDependencies = Seq(
    "org.jsoup" % "jsoup" % versions.jsoup
  )

  val logbackDependencies = Seq(
    "ch.qos.logback" % "logback-classic" % versions.logback,
    "ch.qos.logback" % "logback-core" % versions.logback,
    "ch.qos.logback" % "logback-access" % versions.logback
  )

  val diffJsonDependencies = Seq(
    "org.gnieh" %% f"diffson-circe" % versions.diffJson % "test"
  )
}

object CatalogueDependencies {
  val internalModelDependencies: Seq[ModuleID] =
    WellcomeDependencies.fixturesLibrary ++
      WellcomeDependencies.elasticsearchLibrary ++
      WellcomeDependencies.elasticsearchTypesafeLibrary ++
      WellcomeDependencies.jsonLibrary ++
      ExternalDependencies.scalacheckDependencies ++
      ExternalDependencies.enumeratumDependencies ++
      ExternalDependencies.scalaXmlDependencies ++
      WellcomeDependencies.storageLibrary ++
      ExternalDependencies.diffJsonDependencies

  val displayModelDependencies: Seq[ModuleID] =
    WellcomeDependencies.httpLibrary

  val flowDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val lambdaDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.elasticsearchTypesafeLibrary ++
      WellcomeDependencies.jsonLibrary ++
      WellcomeDependencies.fixturesLibrary ++
      ExternalDependencies.scalatestDependencies ++
      ExternalDependencies.awsLambdaClient ++
      ExternalDependencies.awsLambdaEvents ++
      ExternalDependencies.uPickle

  val sourceModelDependencies: Seq[sbt.ModuleID] =
    WellcomeDependencies.storageLibrary ++
      WellcomeDependencies.fixturesLibrary ++
      WellcomeDependencies.sierraLibrary ++
      ExternalDependencies.scalatestDependencies ++
      ExternalDependencies.logbackDependencies

  val sourceModelTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageTypesafeLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary

  val pipelineStorageDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingLibrary ++
      WellcomeDependencies.typesafeLibrary

  val pipelineStorageTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary

  val transformerCommonDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageLibrary ++
      ExternalDependencies.parseDependencies ++ ExternalDependencies.circeOpticsDependencies

  val transformerMarcCommonDependencies: Seq[ModuleID] =
    Nil

  val transformerMarcXMLDependencies: Seq[ModuleID] = Nil

  val idminterDependencies: Seq[ModuleID] =
    ExternalDependencies.mySqlDependencies ++
      ExternalDependencies.circeOpticsDependencies ++
      ExternalDependencies.scalatestDependencies

  val matcherDependencies: Seq[ModuleID] =
    ExternalDependencies.scalaGraphDependencies ++
      // Matcher requires SSO in order to connect to DynamoDB
      // when running in a local RIE lambda container.
      // This is not required for normal running.
      ExternalDependencies.awsSSODependencies ++
      WellcomeDependencies.storageTypesafeLibrary

  val mergerDependencies: Seq[ModuleID] = Nil

  val pathConcatenatorDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary

  val routerDependencies: Seq[ModuleID] =
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
      ExternalDependencies.scalacsvDependencies ++
      WellcomeDependencies.jsonLibrary ++
      WellcomeDependencies.sierraLibrary

  val metsTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.apacheCommonsDependencies

  val calmTransformerDependencies: Seq[ModuleID] =
    ExternalDependencies.jsoupDependencies ++
      ExternalDependencies.parseDependencies

  // METS adapter

  val metsAdapterDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.storageTypesafeLibrary ++
      WellcomeDependencies.httpLibrary

  // CALM adapter

  val calmApiClientDependencies: Seq[ModuleID] =
    ExternalDependencies.scalaXmlDependencies ++
      ExternalDependencies.scalatestDependencies ++
      WellcomeDependencies.httpLibrary

  val calmIndexerDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.storageLibrary ++
      WellcomeDependencies.elasticsearchTypesafeLibrary

  // Sierra adapter stack

  val sierraLinkerDependencies: Seq[ModuleID] =
    WellcomeDependencies.sierraTypesafeLibrary ++
      WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.storageTypesafeLibrary ++
      WellcomeDependencies.typesafeLibrary

  val sierraMergerDependencies: Seq[ModuleID] =
    WellcomeDependencies.sierraTypesafeLibrary ++
      WellcomeDependencies.typesafeLibrary

  val sierraIndexerDependencies: Seq[ModuleID] =
    WellcomeDependencies.storageLibrary

  // Inference manager
  val inferenceManagerDependencies: Seq[ModuleID] =
    WellcomeDependencies.httpLibrary

  // TEI adapter

  val teiIdExtractorDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.httpLibrary ++
      WellcomeDependencies.typesafeLibrary ++
      WellcomeDependencies.storageLibrary ++
      ExternalDependencies.mySqlDependencies ++
      ExternalDependencies.scalatestDependencies

  val teiAdapterServiceDependencies: Seq[ModuleID] =
    WellcomeDependencies.messagingTypesafeLibrary ++
      WellcomeDependencies.typesafeLibrary ++
      WellcomeDependencies.storageTypesafeLibrary ++
      ExternalDependencies.scalatestDependencies
}
