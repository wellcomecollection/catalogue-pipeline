import sbt._

object WellcomeDependencies {
  lazy val versions = new {
    val json = "1.1.1"
  }

  val jsonLibrary: Seq[ModuleID] = library(
    name    = "json",
    version = versions.json
  )

  private def library(name: String, version: String): Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% name % version,
    "uk.ac.wellcome" %% name % version % "test" classifier "tests"
  )
}

object ExternalDependencies {
  lazy val versions = new {
    val finatra             = "18.11.0"
    val guice               = "4.2.0"
    val scalacheck          = "1.13.4"
    val scalacheckShapeless = "1.1.6"
    val scalacsv            = "1.3.5"
    val scalatest           = "3.0.1"
  }

  val guiceDependencies = Seq(
    "com.google.inject" % "guice" % versions.guice,
    "com.google.inject.extensions" % "guice-testlib" % versions.guice % "test"
  )

  val scalacheckDependencies = Seq(
    "org.scalacheck" %% "scalacheck" % versions.scalacheck % "test",
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % versions.scalacheckShapeless % "test"
  )

  val scalacsvDependencies = Seq(
    "com.github.tototoshi" %% "scala-csv" % versions.scalacsv
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
}
