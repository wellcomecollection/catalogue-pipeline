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

object Dependencies {
  lazy val versions = new {
    val scalaCsv = "1.3.5"
    val scalatest = "3.0.1"
  }

  val sharedDependencies = Seq(
    "org.scalatest" %% "scalatest" % versions.scalatest % "test"
  )

  val internalModelDependencies = Seq(
    "com.github.tototoshi" %% "scala-csv" % versions.scalaCsv
  ) ++ WellcomeDependencies.jsonLibrary
}
