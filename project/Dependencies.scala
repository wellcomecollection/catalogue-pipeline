import sbt._

object WellcomeDependencies {
  lazy val versions = new {
    val fixtures  = "1.0.0"
    val json      = "1.1.1"
    val messaging = "1.2.0"
    val typesafe  = "1.0.0"
  }

  val jsonLibrary: Seq[ModuleID] = library(
    name    = "json",
    version = versions.json
  )

  val fixturesLibrary: Seq[ModuleID] = library(
    name = "fixtures",
    version = versions.fixtures
  )

  val messagingTypesafeLibrary: Seq[ModuleID] = library(
    name = "messaging_typesafe",
    version = versions.messaging
  )

  val typesafeLibrary: Seq[ModuleID] = library(
    name = "typesafe-app",
    version = versions.typesafe
  )

  private def library(name: String, version: String): Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% name % version,
    "uk.ac.wellcome" %% name % version % "test" classifier "tests"
  )
}

object ExternalDependencies {
  lazy val versions = new {
    val apacheLogging       = "2.8.2"
    val aws                 = "1.11.95"
    val circe               = "0.9.0"
    val elastic4s           = "6.5.0"
    val finatra             = "18.11.0"
    val guice               = "4.2.0"
    val scalacheck          = "1.13.4"
    val scalacheckShapeless = "1.1.6"
    val scalacsv            = "1.3.5"
    val scalaGraph          = "1.12.5"
    val scalatest           = "3.0.1"
  }

  val circeOpticsDependencies = Seq(
    "io.circe" %% "circe-optics" % versions.circe
  )

  val elasticsearchDependencies = Seq(
    "org.apache.logging.log4j" % "log4j-core" %            versions.apacheLogging,
    "org.apache.logging.log4j" % "log4j-api" %             versions.apacheLogging,
    "com.sksamuel.elastic4s" %% "elastic4s-core" %         versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-http" %         versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-http-streams" % versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-testkit" %      versions.elastic4s % "test"
  )

  val guiceDependencies = Seq(
    "com.google.inject" % "guice" % versions.guice,
    "com.google.inject.extensions" % "guice-testlib" % versions.guice % "test"
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
}

object CatalogueDependencies {
  val sharedDependencies =
    ExternalDependencies.scalatestDependencies

  val internalModelDependencies =
    ExternalDependencies.scalacsvDependencies ++
    WellcomeDependencies.jsonLibrary

  val displayModelDependencies =
    ExternalDependencies.swaggerDependencies ++
    ExternalDependencies.guiceDependencies ++
    ExternalDependencies.scalacheckDependencies

  val elasticsearchDependencies: Seq[ModuleID] =
    ExternalDependencies.elasticsearchDependencies ++
    ExternalDependencies.scalacheckDependencies ++
    WellcomeDependencies.fixturesLibrary

  val elasticsearchTypesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.typesafeLibrary

  val goobiReaderDependencies: Seq[ModuleID] =
    WellcomeDependencies.jsonLibrary ++
    WellcomeDependencies.messagingTypesafeLibrary

  val idminterDependencies: Seq[ModuleID] =
    ExternalDependencies.mySqlDependencies ++
    ExternalDependencies.circeOpticsDependencies ++
    WellcomeDependencies.messagingTypesafeLibrary

  val matcherDependencies: Seq[ModuleID] =
    ExternalDependencies.scalaGraphDependencies ++
    WellcomeDependencies.messagingTypesafeLibrary

  val miroTransformerDependencies: Seq[ModuleID] = Seq(
    "org.apache.commons" % "commons-lang3" % "3.1"
  ) ++ WellcomeDependencies.messagingTypesafeLibrary
}
