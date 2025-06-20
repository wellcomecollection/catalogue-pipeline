package weco.pipeline.merger

import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.lambda._
import weco.pipeline.merger.config.{MergerConfig, MergerConfigurable}
import weco.pipeline.merger.services.{
  IdentifiedWorkLookup,
  MergerManager,
  PlatformMerger,
  WorkRouter
}
import weco.pipeline_storage.EitherIndexer
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}

object Main
    extends MergerSQSLambda[MergerConfig]
    with MergerConfigurable
    with Logging {

  private val esClient = ElasticBuilder.buildElasticClient(config.elasticConfig)
  private val retriever =
    new ElasticSourceRetriever[Work[Identified]](
      client = esClient,
      index = Index(config.identifiedWorkIndex)
    )
  private val sourceWorkLookup = new IdentifiedWorkLookup(retriever)

  private val mergerManager = new MergerManager(PlatformMerger)

  private val workDownstream = Downstream(config.workDownstreamTarget)
  private val pathDownstream = Downstream(config.pathDownstreamTarget)
  private val pathConcatDownstream = Downstream(
    config.pathConcatDownstreamTarget
  )

  private val workOrImageIndexer = {
    new EitherIndexer[Work[Merged], Image[Initial]](
      leftIndexer = new ElasticIndexer[Work[Merged]](
        client = esClient,
        index = Index(config.denormalisedWorkIndex)
      ),
      rightIndexer = new ElasticIndexer[Image[Initial]](
        client = esClient,
        index = Index(config.initialImageIndex)
      )
    )
  }

  // does this belong here?
  type WorkOrImage = Either[Work[Merged], Image[Initial]]

  override protected val workRouter: WorkRouter = new WorkRouter(
    workSender = workDownstream,
    pathSender = pathDownstream,
    pathConcatenatorSender = pathConcatDownstream
  )
  override protected val imageMsgSender: Downstream = Downstream(
    config.imageDownstreamTarget
  )

  override protected val mergeProcessor = new MergeProcessor(
    sourceWorkLookup,
    mergerManager,
    workOrImageIndexer
  )
}
