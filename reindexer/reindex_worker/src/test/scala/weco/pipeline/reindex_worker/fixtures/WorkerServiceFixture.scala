package weco.pipeline.reindex_worker.fixtures

import weco.pekko.fixtures.Pekko
import weco.fixtures.{RandomGenerators, TestWith}
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryIndividualMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline.reindex_worker.models._
import weco.pipeline.reindex_worker.services.{
  BulkMessageSender,
  RecordReader,
  ReindexWorkerService
}
import weco.storage.fixtures.DynamoFixtures.Table

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends Pekko
    with SQS
    with ReindexDynamoFixtures
    with RandomGenerators {
  val defaultJobConfigId = "testing"

  type Destination = String

  def createDestination: Destination =
    randomAlphanumeric()

  def withWorkerService[R](
    messageSender: MemoryIndividualMessageSender,
    queue: Queue,
    configMap: Map[String, (Table, Destination, ReindexSource)]
  )(testWith: TestWith[ReindexWorkerService[Destination], R]): R =
    withActorSystem {
      implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) {
          sqsStream =>
            val workerService = new ReindexWorkerService(
              recordReader = new RecordReader,
              bulkMessageSender =
                new BulkMessageSender[Destination](messageSender),
              sqsStream = sqsStream,
              reindexJobConfigMap = configMap.map {
                case (key: String, (table, destination, source)) =>
                  key -> ReindexJobConfig(
                    dynamoConfig = createDynamoConfigWith(table),
                    destinationConfig = destination,
                    source = source
                  )
              }
            )

            workerService.run()

            testWith(workerService)
        }
    }

  def chooseReindexSource: ReindexSource =
    chooseFrom(
      ReindexSource.Calm,
      ReindexSource.Mets,
      ReindexSource.Miro,
      ReindexSource.Sierra
    )

  def withWorkerService[R](
    messageSender: MemoryIndividualMessageSender,
    queue: Queue,
    table: Table,
    destination: Destination,
    source: ReindexSource = chooseReindexSource
  )(testWith: TestWith[ReindexWorkerService[Destination], R]): R =
    withWorkerService(
      messageSender,
      queue,
      configMap = Map(defaultJobConfigId -> ((table, destination, source)))
    ) {
      service =>
        testWith(service)
    }

  private val defaultParameters = CompleteReindexParameters(
    segment = 0,
    totalSegments = 1
  )

  def createReindexRequestWith(
    jobConfigId: String = defaultJobConfigId,
    parameters: ReindexParameters = defaultParameters
  ): ReindexRequest =
    ReindexRequest(
      jobConfigId = jobConfigId,
      parameters = parameters
    )

  def createReindexRequest: ReindexRequest = createReindexRequestWith()
}
