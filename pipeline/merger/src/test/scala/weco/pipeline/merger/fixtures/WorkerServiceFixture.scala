package weco.pipeline.merger.fixtures

import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.monitoring.Metrics
import weco.monitoring.memory.MemoryMetrics
import weco.pipeline.merger.services.{
  IdentifiedWorkLookup,
  MergerManager,
  MergerWorkerService,
  PlatformMerger
}
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WorkerServiceFixture extends PipelineStorageStreamFixtures {

  type WorkOrImage = Either[Work[Merged], Image[Initial]]

  def withWorkerService[R](
    retriever: MemoryRetriever[Work[Identified]],
    queue: Queue,
    workSender: MemoryMessageSender,
    imageSender: MemoryMessageSender = new MemoryMessageSender(),
    metrics: Metrics[Future] = new MemoryMetrics,
    index: mutable.Map[String, WorkOrImage] = mutable.Map.empty)(
    testWith: TestWith[MergerWorkerService[String, String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { msgStream =>
        val workerService = new MergerWorkerService(
          msgStream = msgStream,
          sourceWorkLookup = new IdentifiedWorkLookup(retriever),
          mergerManager = new MergerManager(PlatformMerger),
          workOrImageIndexer = new MemoryIndexer(index),
          workMsgSender = workSender,
          imageMsgSender = imageSender,
          config = pipelineStorageConfig
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def withWorkerService[R](retriever: MemoryRetriever[Work[Identified]])(
    testWith: TestWith[MergerWorkerService[String, String], R]): R =
    withLocalSqsQueue() { queue =>
      val workSender = new MemoryMessageSender()
      val imageSender = new MemoryMessageSender()

      withWorkerService(retriever, queue, workSender, imageSender) {
        workerService =>
          testWith(workerService)
      }
    }

  def getWorksSent(workSender: MemoryMessageSender): Seq[String] =
    workSender.messages.map { _.body }

  def getImagesSent(imageSender: MemoryMessageSender): Seq[String] =
    imageSender.messages.map { _.body }
}
