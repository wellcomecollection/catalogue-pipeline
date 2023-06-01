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
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.idminterDependencies
)

lazy val ingestor_common = setupProject(
  project,
  "pipeline/ingestor/ingestor_common",
  localDependencies = Seq(pipeline_storage_typesafe, display_model),
  externalDependencies = WellcomeDependencies.elasticsearchTypesafeLibrary
)

lazy val ingestor_works = setupProject(
  project,
  "pipeline/ingestor/ingestor_works",
  localDependencies = Seq(ingestor_common)
)

lazy val ingestor_images = setupProject(
  project,
  "pipeline/ingestor/ingestor_images",
  localDependencies = Seq(ingestor_common)
)

lazy val matcher = setupProject(
  project,
  "pipeline/matcher",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.matcherDependencies
)

lazy val merger = setupProject(
  project,
  "pipeline/merger",
  localDependencies = Seq(internal_model, matcher, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.mergerDependencies
)

lazy val path_concatenator = setupProject(
  project,
  "pipeline/relation_embedder/path_concatenator",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.pathConcatenatorDependencies
)

lazy val relation_embedder = setupProject(
  project,
  "pipeline/relation_embedder/relation_embedder",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.relationEmbedderDependencies
)

lazy val router = setupProject(
  project,
  "pipeline/relation_embedder/router",
  localDependencies = Seq(internal_model, pipeline_storage_typesafe),
  externalDependencies = CatalogueDependencies.routerDependencies
)

lazy val batcher = setupProject(
  project,
  "pipeline/relation_embedder/batcher",
  localDependencies = Seq(
    // Strictly speaking, the batcher doesn't need any of the internal model code,
    // but for some reason the batcher tests fail if we don't include it in the
    // class path:
    //
    //        Cause: software.amazon.awssdk.core.exception.SdkClientException: Unable to execute HTTP request: The connection was closed during the request. The request will usually succeed on a retry, but if it does not: consider disabling any proxies you have configured, enabling debug logging, or performing a TCP dump to identify the root cause. If this is a streaming operation, validate that data is being read or written in a timely manner. Channel Information: ChannelDiagnostics(channel=[id: 0x49117641, L:0.0.0.0/0.0.0.0:38210], channelAge=PT0.000813S, requestCount=1)
    //        at software.amazon.awssdk.core.exception.SdkClientException$BuilderImpl.build(SdkClientException.java:111)
    //        at software.amazon.awssdk.core.exception.SdkClientException.create(SdkClientException.java:47)
    //        at software.amazon.awssdk.core.internal.http.pipeline.stages.utils.RetryableStageHelper.setLastException(RetryableStageHelper.java:223)
    //        at software.amazon.awssdk.core.internal.http.pipeline.stages.utils.RetryableStageHelper.setLastException(RetryableStageHelper.java:218)
    //        at software.amazon.awssdk.core.internal.http.pipeline.stages.AsyncRetryableStage$RetryingExecutor.maybeRetryExecute(AsyncRetryableStage.java:182)
    //        at software.amazon.awssdk.core.internal.http.pipeline.stages.AsyncRetryableStage$RetryingExecutor.lambda$attemptExecute$1(AsyncRetryableStage.java:159)
    //        at java.base/java.util.concurrent.CompletableFuture.uniWhenComplete(Unknown Source)
    //        at java.base/java.util.concurrent.CompletableFuture$UniWhenComplete.tryFire(Unknown Source)
    //        at java.base/java.util.concurrent.CompletableFuture.postComplete(Unknown Source)
    //        at java.base/java.util.concurrent.CompletableFuture.completeExceptionally(Unknown Source)
    //        ...
    //        Cause: java.io.IOException: The connection was closed during the request. The request will usually succeed on a retry, but if it does not: consider disabling any proxies you have configured, enabling debug logging, or performing a TCP dump to identify the root cause. If this is a streaming operation, validate that data is being read or written in a timely manner. Channel Information: ChannelDiagnostics(channel=[id: 0x49117641, L:0.0.0.0/0.0.0.0:38210], channelAge=PT0.000813S, requestCount=1)
    //        at software.amazon.awssdk.http.nio.netty.internal.NettyRequestExecutor.configurePipeline(NettyRequestExecutor.java:233)
    //        at software.amazon.awssdk.http.nio.netty.internal.NettyRequestExecutor.lambda$makeRequestListener$10(NettyRequestExecutor.java:181)
    //        at io.netty.util.concurrent.PromiseTask.runTask(PromiseTask.java:98)
    //        at io.netty.util.concurrent.PromiseTask.run(PromiseTask.java:106)
    //        at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:174)
    //        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:167)
    //        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:470)
    //        at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:566)
    //        at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
    //        at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
    //        ...
    //
    // We started seeing these issues when we upgraded our Buildkite CI Stack
    // from 5.7.2 to 5.16.1.
    //
    // This is presumably an issue of dependency resolution somewhere, and the batcher
    // is getting a different version of a dependency to all our other apps -- but
    // I can't work out exactly where it is.
    //
    // You can see some experiments trying to find it in this PR, where I created a new
    // copy of internal_model and started cutting bits out:
    // https://github.com/wellcomecollection/catalogue-pipeline/pull/2327
    //
    // But ultimately it wasn't a good use of time to keep debugging this.
    internal_model
  ),
  externalDependencies = CatalogueDependencies.batcherDependencies
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

lazy val transformer_miro = setupProject(
  project,
  folder = "pipeline/transformer/transformer_miro",
  localDependencies = Seq(transformer_common),
  externalDependencies = CatalogueDependencies.miroTransformerDependencies
)

lazy val transformer_sierra = setupProject(
  project,
  folder = "pipeline/transformer/transformer_sierra",
  localDependencies = Seq(transformer_common),
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

lazy val sierra_reader = setupProject(
  project,
  "sierra_adapter/sierra_reader",
  localDependencies = Seq(source_model),
  externalDependencies = CatalogueDependencies.sierraReaderDependencies
)

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
// AWS Credentials to read from S3

s3CredentialsProvider := {
  _ =>
    val builder = new STSAssumeRoleSessionCredentialsProvider.Builder(
      "arn:aws:iam::760097843905:role/platform-ci",
      UUID.randomUUID().toString
    )
    builder.build()
}
