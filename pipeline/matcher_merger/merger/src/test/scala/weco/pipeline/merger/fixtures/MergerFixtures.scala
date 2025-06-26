package weco.pipeline.merger.fixtures

import com.typesafe.config.Config
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.fixtures.TestWith
import weco.lambda.{ApplicationConfig, Downstream, SNSDownstream}
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.SNSConfig
import weco.pipeline.merger.{MergeProcessor, MergerSQSLambda}
import weco.pipeline.merger.services.{IdentifiedWorkLookup, MergerManager, PlatformMerger, WorkRouter}
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

trait MergerFixtures extends PipelineStorageStreamFixtures {
  case class DummyConfig() extends ApplicationConfig
  //  def createIndex(works: List[Work[Source]]): Map[String, Json] =
  //    works.map(work => (work.id, work.asJson)).toMap
  type WorkOrImage = Either[Work[Merged], Image[Initial]]

  val workRouter = new MemoryWorkRouter(
    new MemoryMessageSender(): MemoryMessageSender,
    new MemoryMessageSender(): MemoryMessageSender,
    new MemoryMessageSender(): MemoryMessageSender
  )

  def withMemoryWorkLookup[R](
    identifiedIndex: MemoryRetriever[Work[Identified]]
  )(
    testWith: TestWith[IdentifiedWorkLookup, R]
  ): R = {
    testWith(
      new IdentifiedWorkLookup(identifiedIndex)
    )
  }

  def withMemoryIndexer[R](
    index: mutable.Map[String, WorkOrImage] = mutable.Map.empty
  )(
    testWith: TestWith[MemoryIndexer[WorkOrImage], R]
  ): R = {
    testWith(new MemoryIndexer[WorkOrImage](index = index))
  }

  class MemorySNSDownstream(sender: MemoryMessageSender) extends SNSDownstream(
    SNSConfig("arn:aws:sns:eu-west-1:123456789012:topic")
  ) {
    override protected val msgSender: MemoryMessageSender = sender
  }

  def withImageDownstream[R](sender: MemoryMessageSender)(
    testWith: TestWith[MemorySNSDownstream, R]
  ): R = {
    testWith(new MemorySNSDownstream(sender))
  }

  def withMergerProcessor[R](
    retriever: MemoryRetriever[Work[Identified]],
    index: mutable.Map[String, WorkOrImage],
  )(testWith: TestWith[MergeProcessor, R]): R = {
    val mergeProcessor = new MergeProcessor(
      sourceWorkLookup = new IdentifiedWorkLookup(retriever),
      mergerManager = new MergerManager(PlatformMerger),
      workOrImageIndexer = new MemoryIndexer(index)
    )
    testWith(mergeProcessor)
  }

  def withMergerSQSLambda[R](
    identifiedIndex: MemoryRetriever[Work[Identified]],
    mergedIndex: mutable.Map[String, WorkOrImage] = mutable.Map.empty,
    workSender: MemoryMessageSender,
    pathSender: MemoryMessageSender,
    pathconcatSender: MemoryMessageSender,
    imageSender: MemoryMessageSender,
  )(
    testWith: TestWith[MergerSQSLambda[DummyConfig], R]
  ): R = {
    withMemoryWorkLookup(identifiedIndex) {
      retriever =>
        withMemoryIndexer(mergedIndex) {
          indexer =>
            withImageDownstream(imageSender) {
              imageDownstream =>
                testWith(new MergerSQSLambda[DummyConfig] {
                  override def build(rawConfig: Config): DummyConfig =
                    DummyConfig()

                  override protected val mergeProcessor: MergeProcessor = {
                    new MergeProcessor(
                      retriever,
                      new MergerManager(PlatformMerger),
                      indexer
                    )(global)
                  }
                  override protected val workRouter: WorkRouter = new MemoryWorkRouter(
                    workSender,
                    pathSender,
                    pathconcatSender
                  )
                  override protected val imageMsgSender: Downstream = imageDownstream
                })
            }
        }
    }
  }

  class MemoryWorkRouter (
    val workSender: MemoryMessageSender,
    val pathSender: MemoryMessageSender,
    val pathConcatenatorSender: MemoryMessageSender
    ) extends WorkRouter(
      workSender = new MemorySNSDownstream(workSender),
      pathSender = new MemorySNSDownstream(pathSender),
      pathConcatenatorSender = new MemorySNSDownstream(pathConcatenatorSender),
    )

  def getWorksSent(workSender: MemoryMessageSender): Seq[String] = {
    workSender.messages.map { _.body }
  }

  def getPathsSent(pathSender: MemoryMessageSender): Seq[String] =
    pathSender.messages.map { _.body }

  def getIncompletePathSent(incompletePathSender: MemoryMessageSender): Seq[String] = {
    incompletePathSender.messages.map { _.body }
  }

  def getImagesSent(imageSender: MemoryMessageSender): Seq[String] =
    imageSender.messages.map { _.body }
}
