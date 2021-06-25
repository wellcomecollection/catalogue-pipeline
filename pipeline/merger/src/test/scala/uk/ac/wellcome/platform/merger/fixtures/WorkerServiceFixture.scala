package uk.ac.wellcome.platform.merger.fixtures

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import weco.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.pipeline_storage.{MemoryIndexer, MemoryRetriever}
import uk.ac.wellcome.platform.merger.services._
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work

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
