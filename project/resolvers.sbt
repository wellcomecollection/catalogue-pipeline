// Plugin resolution for the meta-build. Mirrors Common.scala, which only
// covers the application projects; plugins are resolved before it compiles.
externalResolvers := {
  val codeArtifact = sys.env.get("CODEARTIFACT_AUTH_TOKEN").filter(_.nonEmpty).map(_ =>
    "CodeArtifact" at "https://wellcomecollection-maven-mirror-760097843905.d.codeartifact.eu-west-1.amazonaws.com/maven/wellcomecollection-maven-mirror/"
  ).toSeq
  Seq(Resolver.defaultLocal) ++ codeArtifact ++ Seq(Resolver.DefaultMavenRepository)
}

credentials ++= sys.env.get("CODEARTIFACT_AUTH_TOKEN").filter(_.nonEmpty).map(token =>
  Credentials(
    "wellcomecollection-maven-mirror/wellcomecollection-maven-mirror",
    "wellcomecollection-maven-mirror-760097843905.d.codeartifact.eu-west-1.amazonaws.com",
    "aws",
    token
  )
).toSeq
