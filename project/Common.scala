import com.tapad.docker.DockerComposePlugin
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.Keys._
import sbt._

import java.io.File

object Common {
  def createSettings(projectVersion: String): Seq[Def.Setting[_]] = Seq(
    scalaVersion := "2.12.8",
    organization := "uk.ac.wellcome",
    resolvers ++= Seq(
      "Wellcome releases" at "s3://releases.mvn-repo.wellcomecollection.org/"),
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
    updateOptions := updateOptions.value.withCachedResolution(true),
    parallelExecution in Test := false,
    publishMavenStyle := true,
    publishTo := Some(
      "S3 releases" at "s3://releases.mvn-repo.wellcomecollection.org/"
    ),
    publishArtifact in Test := true,
    // Don't build scaladocs
    // https://www.scala-sbt.org/sbt-native-packager/formats/universal.html#skip-packagedoc-task-on-stage
    mappings in (Compile, packageDoc) := Nil,
    version := projectVersion
  )
  def setupProject(
                    project: Project,
                    folder: String,
                    projectVersion: String,
                    localDependencies: Seq[Project] = Seq(),
                    externalDependencies: Seq[ModuleID] = Seq()
                  ): Project = {
    val settings = createSettings(projectVersion)
    Metadata.write(project, folder, localDependencies)

    val dependsOn = localDependencies
      .map { project: Project =>
        ClasspathDependency(
          project = project,
          configuration = Some("compile->compile;test->test")
        )
      }

    project
      .in(new File(folder))
      .settings(settings: _*)
      .settings(DockerCompose.settings: _*)
      .enablePlugins(DockerComposePlugin)
      .enablePlugins(JavaAppPackaging)
      .dependsOn(dependsOn: _*)
      .settings(libraryDependencies ++= externalDependencies)
  }

}
