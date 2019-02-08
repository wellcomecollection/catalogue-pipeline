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

lazy val reindex_worker = setupProject(project, "reindexer/reindex_worker",
  externalDependencies = WellcomeDependencies.messagingTypesafeLibrary
)
