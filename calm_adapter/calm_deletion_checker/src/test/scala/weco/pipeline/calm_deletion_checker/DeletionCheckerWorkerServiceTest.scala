package weco.pipeline.calm_deletion_checker

import akka.Done
import akka.http.scaladsl.model.headers.Cookie
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.storage.fixtures.DynamoFixtures
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.catalogue.source_model.Implicits._
import weco.pipeline.calm_api_client.{
  CalmQuery,
  CalmSession,
  QueryLeaf,
  QueryNode
}
import weco.pipeline.calm_api_client.fixtures.CalmApiClientFixtures
import weco.pipeline.calm_deletion_checker.fixtures.{
  CalmSourcePayloadGenerators,
  DynamoCalmVHSFixture
}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.higherKinds

class DeletionCheckerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with DynamoCalmVHSFixture
    with SQS
    with CalmApiClientFixtures
    with CalmRecordGenerators
    with CalmSourcePayloadGenerators {

  it("marks Calm records that are missing from the API as deleted in Dynamo") {
    val storeRecords = (1 to 30).map(_ => createCalmRecord)
    val deletedRecords = randomSample(storeRecords, size = 5)
    val extantRecordIds = (storeRecords.toSet -- deletedRecords.toSet).map(_.id)

    withTestCalmApiClient(
      handleSearch = searchHandler(extantRecordIds),
      handleAbandon = abandonHandler
    ) {
      apiClient =>
        withDynamoSourceVHS(storeRecords) {
          case (_, sourceTable, getRows) =>
            withDeletionCheckerWorkerService(apiClient, sourceTable) {
              case (QueuePair(queue, dlq), messageSender) =>
                getRows().map(_.toPayload).foreach {
                  payload =>
                    sendNotificationToSQS(queue, payload)
                }

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueEmpty(dlq)

                  val deletedRowIds = getRows().filter(_.isDeleted).map(_.id)

                  deletedRowIds should contain theSameElementsAs deletedRecords
                    .map(_.id)
                  messageSender
                    .getMessages[CalmSourcePayload]()
                    .map(_.id) should
                    contain theSameElementsAs deletedRowIds
                }
            }
        }
    }
  }

  it("ignores records that are already marked as deleted") {
    val storeRecords = (1 to 30).map(_ => createCalmRecord)
    val deletedRecords = randomSample(storeRecords, size = 5)
    val extantRecordIds = (storeRecords.toSet -- deletedRecords.toSet).map(_.id)

    withTestCalmApiClient(
      handleSearch = searchHandler(extantRecordIds),
      handleAbandon = abandonHandler
    ) {
      apiClient =>
        withDynamoSourceVHS(storeRecords) {
          case (_, sourceTable, getRows) =>
            val alreadyDeletedIds =
              randomSample(deletedRecords, size = 2).map(_.id)
            val alreadyDeletedRows = getRows().collect {
              case row if alreadyDeletedIds.contains(row.id) =>
                row.copy(isDeleted = true)
            }
            putTableItems(
              alreadyDeletedRows,
              sourceTable
            )

            withDeletionCheckerWorkerService(apiClient, sourceTable) {
              case (QueuePair(queue, dlq), messageSender) =>
                getRows().map(_.toPayload).foreach {
                  payload =>
                    sendNotificationToSQS(queue, payload)
                }

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueEmpty(dlq)

                  val messageIds = messageSender
                    .getMessages[CalmSourcePayload]()
                    .map(_.id)

                  messageIds should have size (deletedRecords.size - alreadyDeletedIds.size)
                  messageIds should contain noElementsOf alreadyDeletedIds
                }
            }
        }
    }
  }

  it("sends messages to the DLQ if checking for deletions fails") {
    val storeRecords = (1 to 10).map(_ => createCalmRecord)
    val badRecordIds = randomSample(storeRecords, size = 2).map(_.id).toSet
    val handleSearch = (q: CalmQuery) => {
      val queryIds = recordIds(q).toSet
      if ((queryIds intersect badRecordIds).nonEmpty) {
        throw new RuntimeException("boom!")
      } else {
        CalmSession(numHits = queryIds.size, cookie = Cookie("key", "value"))
      }
    }

    withTestCalmApiClient(
      handleSearch = handleSearch,
      handleAbandon = abandonHandler
    ) {
      apiClient =>
        withDynamoSourceVHS(storeRecords) {
          case (_, sourceTable, getRows) =>
            withDeletionCheckerWorkerService(
              apiClient,
              sourceTable,
              visibilityTimeout = 1 second
            ) {
              case (QueuePair(queue, dlq), _) =>
                getRows().map(_.toPayload).foreach {
                  payload =>
                    sendNotificationToSQS(queue, payload)
                }

                eventually {
                  assertQueueEmpty(queue)

                  // Because of the batching the total number of DLQ messages
                  // will almost certainly be larger than the number of bad records
                  getMessages(dlq).size should be >= badRecordIds.size
                }
            }
        }
    }
  }

  it("sends messages to the DLQ if marking records as deleted fails") {
    val storeRecords = (1 to 10).map(_ => createCalmRecord)

    withTestCalmApiClient(
      handleSearch = searchHandler(storeRecords.map(_.id).toSet),
      handleAbandon = abandonHandler
    ) {
      apiClient =>
        withDynamoSourceVHS(storeRecords) {
          case (_, sourceTable, getRows) =>
            withDeletionCheckerWorkerService(
              apiClient,
              sourceTable,
              visibilityTimeout = 1 second
            ) {
              case (QueuePair(queue, dlq), _) =>
                val storedPayloads = getRows().map(_.toPayload)
                val phantomPayloads = (1 to 3).map(_ => calmSourcePayload)
                (storedPayloads ++ phantomPayloads).foreach {
                  payload =>
                    sendNotificationToSQS(queue, payload)
                }

                eventually {
                  assertQueueEmpty(queue)

                  getRows().map(
                    _.id
                  ) should contain noElementsOf phantomPayloads
                    .map(_.id)

                  // Because of the batching the total number of DLQ messages
                  // will almost certainly be larger than the number of bad records
                  getMessages(dlq).size should be >= phantomPayloads.size
                }
            }
        }
    }
  }

  def withDeletionCheckerWorkerService[R](
    apiClient: TestCalmApiClient,
    sourceTable: DynamoFixtures.Table,
    batchSize: Int = 10,
    batchDuration: FiniteDuration = 100 milliseconds,
    visibilityTimeout: Duration = 5 seconds
  )(testWith: TestWith[(QueuePair, MemoryMessageSender), R]): R =
    withActorSystem {
      implicit actorSystem =>
        withLocalSqsQueuePair(visibilityTimeout = visibilityTimeout) {
          case queuePair @ QueuePair(queue, _) =>
            withSQSStream[NotificationMessage, R](queue) {
              stream =>
                implicit val ec: ExecutionContext = actorSystem.dispatcher
                val messageSender = new MemoryMessageSender()
                val deletionMarker = new DeletionMarker(sourceTable.name)
                val workerService = new DeletionCheckerWorkerService(
                  messageStream = stream,
                  messageSender = messageSender,
                  markDeleted = deletionMarker,
                  calmApiClient = apiClient,
                  batchSize = batchSize,
                  batchDuration = batchDuration
                )
                workerService.run()
                testWith((queuePair, messageSender))
            }
        }
    }

  def searchHandler(idsInApi: Set[String]): CalmQuery => CalmSession =
    (q: CalmQuery) => {
      val extantRecordsInQuery = recordIds(q).toSet intersect idsInApi
      CalmSession(
        numHits = extantRecordsInQuery.size,
        cookie = Cookie("key", "value")
      )
    }

  def abandonHandler: Cookie => Done = _ => Done

  def recordIds(q: CalmQuery): Seq[String] = q match {
    case QueryLeaf("RecordId", id, _) => Seq(
      // Strip quotes from the query
      id.replace("\"", "")
    )
    case QueryNode(left, right, _) =>
      recordIds(left) ++ recordIds(right)
    case _ => Nil
  }

}
