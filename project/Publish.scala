import sbt.Keys._
import sbt._

object Publish {
  val settings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := true,
    publishTo := Some(
      "S3 releases" at "s3://releases.mvn-repo.wellcomecollection.org/"
    ),
    publishArtifact in Test := true,
    publishArtifact in (Compile, packageDoc) := false
  )
}
