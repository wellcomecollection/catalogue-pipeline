import java.io.File
import java.util.UUID
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider

// At the root of the repository is a directory containing index configurations
// In "Real Life" the indices are configured at deploy time, so the applications
// do not need them.  However, they are needed in some projects for creating
// ephemeral indices for testing.
lazy val indexConfigDir =
  settingKey[File]("Folder in which index configurations are found")
Global / indexConfigDir := baseDirectory.value / "index_config"

def setupProject(
  project: Project,
  folder: String,
  localDependencies: Seq[Project] = Seq(),
  externalDependencies: Seq[ModuleID] = Seq()
): Project = {

  val dependsOn = localDependencies
    .map {
      project: Project =>
        ClasspathDependency(
          project = project,
          configuration = Some("compile->compile;test->test")
        )
    }

  project
    .in(new File(folder))
    .settings(Common.settings: _*)
    .enablePlugins(DockerComposePlugin)
    .enablePlugins(JavaAppPackaging)
    .dependsOn(dependsOn: _*)
    .settings(libraryDependencies ++= externalDependencies)
}

lazy val internal_model = setupProject(
  project,
  "common/internal_model",
  externalDependencies = CatalogueDependencies.internalModelDependencies
).settings(
  // Only needed for generating ephemeral indices for testing
  Test / unmanagedResourceDirectories += indexConfigDir.value
)
lazy val display_model = setupProject(
  project,
  folder = "common/display_model",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.displayModelDependencies
)

lazy val lambda = setupProject(
  project,
  "common/lambda",
  externalDependencies = CatalogueDependencies.lambdaDependencies
)

lazy val flows = setupProject(
  project,
  "common/flows",
  externalDependencies = CatalogueDependencies.flowDependencies
)

lazy val source_model = setupProject(
  project,
  folder = "common/source_model",
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.sourceModelDependencies
)

lazy val source_model_typesafe = setupProject(
  project,
  folder = "common/source_model_typesafe",
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.sourceModelTypesafeDependencies
)

lazy val pipeline_storage = setupProject(
  project,
  "common/pipeline_storage",
  localDependencies = Seq(internal_model, flows),
  externalDependencies = CatalogueDependencies.pipelineStorageDependencies
)

lazy val pipeline_storage_typesafe = setupProject(
  project,
  "common/pipeline_storage_typesafe",
  localDependencies = Seq(pipeline_storage),
  externalDependencies =
    CatalogueDependencies.pipelineStorageTypesafeDependencies
)

lazy val id_minter = setupProject(
  project,
  "pipeline/id_minter",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe, lambda),
  externalDependencies = CatalogueDependencies.idminterDependencies
)

lazy val ingestor_common = setupProject(
  project,
  "pipeline/ingestor/ingestor_common",
  localDependencies = Seq(pipeline_storage_typesafe, display_model),
  externalDependencies = WellcomeDependencies.elasticsearchTypesafeLibrary
)

lazy val ingestor_images = setupProject(
  project,
  "pipeline/ingestor/ingestor_images",
  localDependencies = Seq(ingestor_common)
)

lazy val matcher = setupProject(
  project,
  "pipeline/matcher_merger/matcher",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe, lambda),
  externalDependencies = CatalogueDependencies.matcherDependencies
)

lazy val merger = setupProject(
  project,
  "pipeline/matcher_merger/merger",
  localDependencies = Seq(internal_model, matcher, pipeline_storage_typesafe, lambda),
  externalDependencies = CatalogueDependencies.mergerDependencies
)

lazy val reindex_worker = setupProject(
  project,
  "reindexer/reindex_worker",
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.reindexWorkerDependencies
)

lazy val transformer_common = setupProject(
  project,
  "pipeline/transformer/transformer_common",
  localDependencies =
    Seq(internal_model, source_model_typesafe, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.transformerCommonDependencies
)

lazy val transformer_marc_common = setupProject(
  project,
  folder = "pipeline/transformer/transformer_marc_common",
  localDependencies = Seq(transformer_common),
  externalDependencies = CatalogueDependencies.transformerMarcCommonDependencies
)

lazy val transformer_marc_xml = setupProject(
  project,
  folder = "pipeline/transformer/transformer_marc_xml",
  localDependencies = Seq(transformer_marc_common),
  externalDependencies = CatalogueDependencies.transformerMarcXMLDependencies
)

lazy val transformer_miro = setupProject(
  project,
  folder = "pipeline/transformer/transformer_miro",
  localDependencies = Seq(transformer_common),
  externalDependencies = CatalogueDependencies.miroTransformerDependencies
)

lazy val transformer_sierra = setupProject(
  project,
  folder = "pipeline/transformer/transformer_sierra",
  localDependencies = Seq(transformer_common, transformer_marc_common),
  externalDependencies = CatalogueDependencies.sierraTransformerDependencies
)

lazy val transformer_mets = setupProject(
  project,
  folder = "pipeline/transformer/transformer_mets",
  localDependencies = Seq(transformer_common),
  externalDependencies = CatalogueDependencies.metsTransformerDependencies
)

lazy val transformer_calm = setupProject(
  project,
  folder = "pipeline/transformer/transformer_calm",
  localDependencies = Seq(transformer_common),
  externalDependencies = CatalogueDependencies.calmTransformerDependencies
)

lazy val transformer_tei = setupProject(
  project,
  folder = "pipeline/transformer/transformer_tei",
  localDependencies = Seq(transformer_common)
)

// Sierra adapter

lazy val sierra_merger = setupProject(
  project,
  "sierra_adapter/sierra_merger",
  localDependencies = Seq(source_model_typesafe),
  externalDependencies = CatalogueDependencies.sierraMergerDependencies
)

lazy val sierra_linker = setupProject(
  project,
  folder = "sierra_adapter/sierra_linker",
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.sierraLinkerDependencies
)

lazy val sierra_indexer = setupProject(
  project,
  folder = "sierra_adapter/sierra_indexer",
  localDependencies = Seq(source_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.sierraIndexerDependencies
)

// METS adapter

lazy val mets_adapter = setupProject(
  project,
  folder = "mets_adapter/mets_adapter",
  localDependencies = Seq(source_model, flows),
  externalDependencies = CatalogueDependencies.metsAdapterDependencies
)

// CALM adapter

lazy val calm_api_client = setupProject(
  project,
  folder = "calm_adapter/calm_api_client",
  localDependencies = Seq(source_model, flows),
  externalDependencies = CatalogueDependencies.calmApiClientDependencies
)

lazy val calm_adapter = setupProject(
  project,
  folder = "calm_adapter/calm_adapter",
  localDependencies =
    Seq(calm_api_client, internal_model, source_model_typesafe)
)

lazy val calm_deletion_checker = setupProject(
  project,
  folder = "calm_adapter/calm_deletion_checker",
  localDependencies = Seq(calm_api_client, source_model_typesafe),
  externalDependencies = ExternalDependencies.scalacheckDependencies
)

lazy val calm_indexer = setupProject(
  project,
  folder = "calm_adapter/calm_indexer",
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.calmIndexerDependencies
)

// Inference manager
lazy val inference_manager = setupProject(
  project,
  folder = "pipeline/inferrer/inference_manager",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.inferenceManagerDependencies
)

// TEI adapter
lazy val tei_id_extractor = setupProject(
  project,
  folder = "tei_adapter/tei_id_extractor",
  localDependencies = Seq(flows, source_model),
  externalDependencies = CatalogueDependencies.teiIdExtractorDependencies
)

lazy val tei_adapter = setupProject(
  project,
  folder = "tei_adapter/tei_adapter",
  localDependencies = Seq(source_model, flows),
  externalDependencies = CatalogueDependencies.teiAdapterServiceDependencies
)
