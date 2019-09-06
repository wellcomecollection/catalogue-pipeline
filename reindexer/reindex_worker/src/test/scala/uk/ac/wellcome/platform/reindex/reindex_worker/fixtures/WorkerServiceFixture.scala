package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  CompleteReindexParameters,
  ReindexJobConfig,
  ReindexParameters,
  ReindexRequest
}
import uk.ac.wellcome.platform.reindex.reindex_worker.services.{
  BulkMessageSender,
  ReindexWorkerService
}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait WorkerServiceFixture extends Akka with RecordReaderFixture with SQS {
  val defaultJobConfigId = "testing"

  type Destination = String

  def createDestination: Destination =
    Random.alphanumeric.take(8) mkString

  def withWorkerService[R](messageSender: MemoryIndividualMessageSender,
                           queue: Queue,
                           configMap: Map[String, (Table, Destination)])(
    testWith: TestWith[ReindexWorkerService[Destination], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new ReindexWorkerService(
          recordReader = createRecordReader,
          bulkMessageSender = new BulkMessageSender[Destination](messageSender),
          sqsStream = sqsStream,
          reindexJobConfigMap = configMap.map {
            case (key: String, (table: Table, destination: Destination)) =>
              key -> ReindexJobConfig(
                dynamoConfig = createDynamoConfigWith(table),
                destinationConfig = destination
              )
          }
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def withWorkerService[R](messageSender: MemoryIndividualMessageSender,
                           queue: Queue,
                           table: Table,
                           destination: Destination)(
    testWith: TestWith[ReindexWorkerService[Destination], R]): R =
    withWorkerService(
      messageSender,
      queue,
      configMap = Map(defaultJobConfigId -> ((table, destination)))) {
      service =>
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
