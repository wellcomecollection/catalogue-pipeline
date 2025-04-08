package weco.pipeline.merger.fixtures

import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Identified, Merged}
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.monitoring.Metrics
import weco.monitoring.memory.MemoryMetrics
import weco.pipeline.merger.services.{IdentifiedWorkLookup, MergerManager, MergerWorkerService, PlatformMerger, WorkRouter}
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MergerFixtures extends PipelineStorageStreamFixtures {

  type WorkOrImage = Either[Either[Work[Merged], Work[Denormalised]], Image[Initial]]

  val workRouter = new WorkRouter(
    new MemoryMessageSender(): MemoryMessageSender,
    new MemoryMessageSender(): MemoryMessageSender,
    new MemoryMessageSender(): MemoryMessageSender
  )

  def withMergerService[R](
    retriever: MemoryRetriever[Work[Identified]],
    queue: Queue,
    workRouter: WorkRouter[String],
    imageSender: MemoryMessageSender = new MemoryMessageSender(),
    metrics: Metrics[Future] = new MemoryMetrics,
    index: mutable.Map[String, WorkOrImage] = mutable.Map.empty
  )(testWith: TestWith[MergerWorkerService[String, String], R]): R =
    withActorSystem {
      implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue, metrics) {
          msgStream =>
            val workerService = new MergerWorkerService(
              msgStream = msgStream,
              sourceWorkLookup = new IdentifiedWorkLookup(retriever),
              mergerManager = new MergerManager(PlatformMerger),
              workOrImageIndexer = new MemoryIndexer(index),
              workRouter = workRouter,
              imageMsgSender = imageSender,
              config = pipelineStorageConfig
            )

            workerService.run()

            testWith(workerService)
        }
    }

  def getWorksSent(workSender: MemoryMessageSender): Seq[String] =
    workSender.messages.map { _.body }

  def getImagesSent(imageSender: MemoryMessageSender): Seq[String] =
    imageSender.messages.map { _.body }
}
