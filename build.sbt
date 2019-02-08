import java.io.File

def setupProject(
  project: Project,
  folder: String,
  localDependencies: Seq[Project] = Seq(),
  externalDependencies: Seq[ModuleID] = Seq()
): Project = {

  // And here we actually create the project, with a few convenience wrappers
  // to make defining projects below cleaner.
  val dependsOn = localDependencies
    .map { project: Project =>
      ClasspathDependency(
        project = project,
        configuration = Some("compile->compile;test->test")
      )
    }

  project
    .in(new File(folder))
    .settings(Common.settings: _*)
    .settings(DockerCompose.settings: _*)
    .enablePlugins(DockerComposePlugin)
    .enablePlugins(JavaAppPackaging)
    .dependsOn(dependsOn: _*)
    .settings(libraryDependencies ++= externalDependencies)
}

lazy val internal_model = setupProject(project, "common/internal_model",
  externalDependencies = CatalogueDependencies.internalModelDependencies
)

lazy val display = setupProject(project, "common/display",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.displayModelDependencies
)

lazy val elasticsearch = setupProject(project, "common/elasticsearch",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.elasticsearchDependencies
)

lazy val elasticsearch_typesafe = setupProject(project, "common/elasticsearch_typesafe",
  localDependencies = Seq(elasticsearch),
  externalDependencies = CatalogueDependencies.elasticsearchTypesafeDependencies
)

lazy val goobi_reader = setupProject(project, "goobi_adapter/goobi_reader",
  externalDependencies = CatalogueDependencies.goobiReaderDependencies
)

lazy val id_minter = setupProject(project, "pipeline/id_minter",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.idminterDependencies
)

lazy val ingestor = setupProject(project, "pipeline/ingestor",
  localDependencies = Seq(elasticsearch_typesafe),
  externalDependencies = WellcomeDependencies.messagingTypesafeLibrary
)

lazy val matcher = setupProject(project, "catalogue_pipeline/matcher",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.matcherDependencies
)

lazy val merger = setupProject(project, "pipeline/merger",
  localDependencies = Seq(internal_model),
  externalDependencies = WellcomeDependencies.messagingTypesafeLibrary
)

lazy val recorder = setupProject(project, "pipeline/recorder",
  localDependencies = Seq(internal_model),
  externalDependencies = WellcomeDependencies.messagingTypesafeLibrary
)

lazy val reindex_worker = setupProject(project, "reindexer/reindex_worker",
  externalDependencies = WellcomeDependencies.messagingTypesafeLibrary
)

lazy val transformer_miro = setupProject(project,
  folder = "pipeline/transformer/transformer_miro",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.miroTransformerDependencies
)

lazy val transformer_sierra = setupProject(project,
  folder = "pipeline/transformer/transformer_sierra",
  localDependencies = Seq(internal_model),
  externalDependencies = WellcomeDependencies.messagingTypesafeLibrary
)
