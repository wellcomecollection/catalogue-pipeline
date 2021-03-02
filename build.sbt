import java.io.File
import java.util.UUID
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider

val projectVersion = "0.0.0"

lazy val internal_model = Common.setupProject(
  project,
  "common/internal_model",
  projectVersion,
  externalDependencies = CatalogueDependencies.internalModelDependencies)

lazy val display = Common.setupProject(
  project,
  "common/display",
  projectVersion,
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.displayModelDependencies)

lazy val elasticsearch = Common.setupProject(
  project,
  "common/elasticsearch",
  projectVersion,
  localDependencies = Seq(internal_model),
  externalDependencies = CatalogueDependencies.elasticsearchDependencies)

lazy val elasticsearch_typesafe = Common.setupProject(
  project,
  "common/elasticsearch_typesafe",
  projectVersion,
  localDependencies = Seq(elasticsearch),
  externalDependencies = CatalogueDependencies.elasticsearchTypesafeDependencies
)

lazy val flows = Common.setupProject(
  project,
  "common/flows",
  projectVersion,
  externalDependencies = CatalogueDependencies.flowDependencies)

lazy val source_model = Common.setupProject(
  project,
  folder = "common/source_model",
  projectVersion,
  externalDependencies = CatalogueDependencies.sourceModelDependencies
)

lazy val source_model_typesafe = Common.setupProject(
  project,
  folder = "common/source_model_typesafe",
  projectVersion,
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.sourceModelTypesafeDependencies
)

lazy val pipeline_storage = Common.setupProject(
  project,
  "common/pipeline_storage",
  projectVersion,
  localDependencies = Seq(elasticsearch_typesafe),
  externalDependencies = CatalogueDependencies.pipelineStorageDependencies
)

lazy val pipeline_storage_typesafe = Common.setupProject(
  project,
  "common/pipeline_storage_typesafe",
  projectVersion,
  localDependencies = Seq(pipeline_storage),
  externalDependencies =
    CatalogueDependencies.pipelineStorageTypesafeDependencies
)

lazy val api = Common.setupProject(
  project,
  "api/api",
  projectVersion,
  localDependencies =
    Seq(internal_model, display, elasticsearch, elasticsearch_typesafe),
  externalDependencies = CatalogueDependencies.apiDependencies
)

lazy val id_minter = Common.setupProject(
  project,
  "pipeline/id_minter",
  projectVersion,
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.idminterDependencies
)

lazy val ingestor_common = Common.setupProject(
  project,
  "pipeline/ingestor/ingestor_common",
  projectVersion,
  localDependencies = Seq(elasticsearch_typesafe, pipeline_storage_typesafe)
)

lazy val ingestor_works = Common.setupProject(
  project,
  "pipeline/ingestor/ingestor_works",
  projectVersion,
  localDependencies = Seq(ingestor_common)
)

lazy val ingestor_images = Common.setupProject(
  project,
  "pipeline/ingestor/ingestor_images",
  projectVersion,
  localDependencies = Seq(ingestor_common)
)

lazy val matcher = Common.setupProject(
  project,
  "pipeline/matcher",
  projectVersion,
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.matcherDependencies
)

lazy val merger = Common.setupProject(
  project,
  "pipeline/merger",
  projectVersion,
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.mergerDependencies
)

lazy val relation_embedder = Common.setupProject(
  project,
  "pipeline/relation_embedder/relation_embedder",
  projectVersion,
  localDependencies =
    Seq(internal_model, elasticsearch, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.relationEmbedderDependencies
)

lazy val router = Common.setupProject(
  project,
  "pipeline/relation_embedder/router",
  projectVersion,
  localDependencies =
    Seq(internal_model, elasticsearch, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.routerDependencies
)

lazy val batcher = Common.setupProject(
  project,
  "pipeline/relation_embedder/batcher",
  projectVersion,
  localDependencies = Seq(),
  externalDependencies = CatalogueDependencies.batcherDependencies
)

lazy val reindex_worker = Common.setupProject(
  project,
  "reindexer/reindex_worker",
  projectVersion,
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.reindexWorkerDependencies)

lazy val transformer_common = Common.setupProject(
  project,
  "pipeline/transformer/transformer_common",
  projectVersion,
  localDependencies =
    Seq(internal_model, source_model_typesafe, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.transformerCommonDependencies
)

lazy val transformer_miro = Common.setupProject(
  project,
  folder = "pipeline/transformer/transformer_miro",
  projectVersion,
  localDependencies = Seq(transformer_common),
  externalDependencies = CatalogueDependencies.miroTransformerDependencies
)

lazy val transformer_sierra = Common.setupProject(
  project,
  folder = "pipeline/transformer/transformer_sierra",
  projectVersion,
  localDependencies = Seq(transformer_common, sierra_adapter_common),
  externalDependencies = CatalogueDependencies.sierraTransformerDependencies
)

lazy val transformer_mets = Common.setupProject(
  project,
  folder = "pipeline/transformer/transformer_mets",
  projectVersion,
  localDependencies = Seq(transformer_common, mets_adapter),
  externalDependencies = CatalogueDependencies.metsTransformerDependencies
)

lazy val transformer_calm = Common.setupProject(
  project,
  folder = "pipeline/transformer/transformer_calm",
  projectVersion,
  localDependencies = Seq(transformer_common, calm_adapter),
  externalDependencies = CatalogueDependencies.calmTransformerDependencies
)

// Sierra adapter

lazy val sierra_adapter_common = Common.setupProject(
  project,
  "sierra_adapter/common",
  projectVersion,
  localDependencies = Seq(source_model_typesafe),
  externalDependencies = CatalogueDependencies.sierraAdapterCommonDependencies
)

lazy val sierra_reader = Common.setupProject(
  project,
  "sierra_adapter/sierra_reader",
  projectVersion,
  localDependencies = Seq(sierra_adapter_common),
  externalDependencies = CatalogueDependencies.sierraReaderDependencies
)

lazy val sierra_merger = Common.setupProject(
  project,
  "sierra_adapter/sierra_merger",
  projectVersion,
  localDependencies = Seq(sierra_adapter_common))

lazy val sierra_linker = Common.setupProject(
  project,
  folder = "sierra_adapter/sierra_linker",
  projectVersion,
  localDependencies = Seq(sierra_adapter_common))

lazy val sierra_indexer = setupProject(
  project,
  folder = "sierra_adapter/sierra_indexer",
  localDependencies = Seq(sierra_adapter_common, pipeline_storage_typesafe))

// METS adapter

lazy val mets_adapter = Common.setupProject(
  project,
  folder = "mets_adapter/mets_adapter",
  projectVersion,
  localDependencies = Seq(internal_model, source_model, flows),
  externalDependencies = CatalogueDependencies.metsAdapterDependencies
)

// CALM adapter

lazy val calm_api_client = Common.setupProject(
  project,
  folder = "calm_adapter/calm_api_client",
  projectVersion,
  localDependencies = Seq(source_model, flows),
  externalDependencies = CatalogueDependencies.calmApiClientDependencies
)

lazy val calm_adapter = Common.setupProject(
  project,
  folder = "calm_adapter/calm_adapter",
  projectVersion,
  localDependencies =
    Seq(calm_api_client, internal_model, source_model_typesafe)
)

lazy val calm_deletion_checker = Common.setupProject(
  project,
  folder = "calm_adapter/calm_deletion_checker",
  projectVersion,
  localDependencies = Seq(calm_api_client, source_model_typesafe),
  externalDependencies = ExternalDependencies.scalacheckDependencies
)

// Inference manager
lazy val inference_manager = Common.setupProject(
  project,
  folder = "pipeline/inferrer/inference_manager",
  projectVersion,
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.inferenceManagerDependencies
)

// Snapshots

lazy val snapshot_generator = Common.setupProject(
  project,
  "snapshots/snapshot_generator",
  projectVersion,
  localDependencies = Seq(internal_model, display, elasticsearch_typesafe),
  externalDependencies = CatalogueDependencies.snapshotGeneratorDependencies
)

// AWS Credentials to read from S3

s3CredentialsProvider := { _ =>
  val builder = new STSAssumeRoleSessionCredentialsProvider.Builder(
    "arn:aws:iam::760097843905:role/platform-ci",
    UUID.randomUUID().toString
  )
  builder.build()
}
