import sbt.Keys._
import sbt._

object Common {
  val settings: Seq[Def.Setting[_]] = Seq(
    scalaVersion := "2.12.8",
    organization := "uk.ac.wellcome",
    resolvers ++= Seq(
      "Wellcome releases" at "https://s3-eu-west-1.amazonaws.com/releases.mvn-repo.wellcomecollection.org/"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-Xlint",
      "-Xverify",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-Ypartial-unification",
      "-Xcheckinit"
    ),
    parallelExecution in Test := false,
    libraryDependencies ++= CatalogueDependencies.commmonDependencies
  )
}
