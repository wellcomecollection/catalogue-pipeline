import com.typesafe.sbt.GitPlugin.autoImport.git
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys}

object Publish {
  val settings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := true,
    publishTo := Some(
      "S3 releases" at "s3://releases.mvn-repo.wellcomecollection.org/"
    ),
    publishArtifact in Test := true,
    publishArtifact in (Compile, packageDoc) := false
  )

  val sharedLibrarySettings: Seq[Def.Setting[_]] = Seq(
    git.baseVersion:= sys.env.getOrElse("BUILDKITE_BUILD_NUMBER","0"),
    git.formattedShaVersion := git.gitHeadCommit.value map { sha => s"${git.baseVersion.value}.$sha" },
    buildInfoKeys := Seq[BuildInfoKey](name, version)
  )
}
