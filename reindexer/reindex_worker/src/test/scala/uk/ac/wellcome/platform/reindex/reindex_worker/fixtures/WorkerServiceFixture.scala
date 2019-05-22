package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{CompleteReindexParameters, ReindexJobConfig, ReindexParameters, ReindexRequest}
import uk.ac.wellcome.platform.reindex.reindex_worker.services.{BulkMessageSender, ReindexWorkerService}
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture extends RecordReaderFixture with SQS {
  val defaultJobConfigId = "testing"

  type DestinationConfig = String

  def withWorkerService[R](queue: Queue,
                           messageSender: MemoryIndividualMessageSender,
                           configMap: Map[String, (Table, DestinationConfig)])(
    testWith: TestWith[ReindexWorkerService[DestinationConfig], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        withRecordReader { recordReader =>
          val workerService = new ReindexWorkerService[DestinationConfig](
            recordReader = recordReader,
            bulkMessageSender = new BulkMessageSender[DestinationConfig](messageSender),
            sqsStream = sqsStream,
            reindexJobConfigMap = configMap.map {
              case (key: String, (table: Table, destination: DestinationConfig)) =>
                key -> ReindexJobConfig(
                  dynamoConfig = createDynamoConfigWith(table),
                  destination = destination
                )
            }
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }



  def withWorkerService[R](queue: Queue, table: Table, messageSender: MemoryIndividualMessageSender, destination: DestinationConfig)(
    testWith: TestWith[ReindexWorkerService[String], R]): R =
    withWorkerService(
      queue,
      messageSender,
      configMap = Map(defaultJobConfigId -> ((table, destination)))) { service =>
      testWith(service)
    }

  private val defaultParameters = CompleteReindexParameters(
    segment = 0,
    totalSegments = 1
  )

  def createReindexRequestWith(
    jobConfigId: String = defaultJobConfigId,
    parameters: ReindexParameters = defaultParameters): ReindexRequest =
    ReindexRequest(
      jobConfigId = jobConfigId,
      parameters = parameters
    )

  def createReindexRequest: ReindexRequest = createReindexRequestWith()
}
