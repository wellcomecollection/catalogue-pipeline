package weco.pipeline.matcher.fixtures

import org.apache.commons.codec.digest.DigestUtils
import org.scanamo.generic.semiauto.deriveDynamoFormat
import org.scanamo.DynamoFormat
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.models.{MatcherResult, WorkNode, WorkStub}
import weco.pipeline.matcher.services.MatcherWorkerService
import weco.pipeline.matcher.storage.{WorkGraphStore, WorkNodeDao}
import weco.pipeline_storage.Retriever
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.MemoryRetriever
import weco.storage.fixtures.DynamoFixtures.Table
import weco.storage.locking.dynamo.{DynamoLockDaoFixtures, ExpiringLock}
import weco.storage.locking.memory.{MemoryLockDao, MemoryLockingService}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds

trait MatcherFixtures
    extends PipelineStorageStreamFixtures
    with DynamoLockDaoFixtures
    with LocalWorkGraphDynamoDb {

  implicit val workNodeFormat: DynamoFormat[WorkNode] = deriveDynamoFormat
  implicit val lockFormat: DynamoFormat[ExpiringLock] = deriveDynamoFormat

  def withWorkGraphTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedTable(createWorkGraphTable) { table =>
      testWith(table)
    }

  def withWorkerService[R](
    retriever: Retriever[WorkStub],
    queue: SQS.Queue,
    messageSender: MemoryMessageSender,
    graphTable: Table)(testWith: TestWith[MatcherWorkerService[String], R]): R =
    withWorkGraphStore(graphTable) { workGraphStore =>
      withWorkMatcher(workGraphStore) { workMatcher =>
        withActorSystem { implicit actorSystem =>
          withSQSStream[NotificationMessage, R](queue) { msgStream =>
            val workerService =
              new MatcherWorkerService(
                pipelineStorageConfig,
                retriever = retriever,
                msgStream,
                messageSender,
                workMatcher)
            workerService.run()
            testWith(workerService)
          }
        }
      }
    }

  def withWorkerService[R](retriever: Retriever[WorkStub],
                           queue: SQS.Queue,
                           messageSender: MemoryMessageSender)(
    testWith: TestWith[MatcherWorkerService[String], R]): R =
    withWorkGraphTable { graphTable =>
      withWorkerService(retriever, queue, messageSender, graphTable) {
        service =>
          testWith(service)
      }
    }

  def withWorkMatcher[R](workGraphStore: WorkGraphStore)(
    testWith: TestWith[WorkMatcher, R]): R = {
    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID]
    val lockingService =
      new MemoryLockingService[MatcherResult, Future]()

    val workMatcher = new WorkMatcher(
      workGraphStore = workGraphStore,
      lockingService = lockingService
    )

    testWith(workMatcher)
  }

  def withWorkGraphStore[R](graphTable: Table)(
    testWith: TestWith[WorkGraphStore, R]): R =
    withWorkNodeDao(graphTable) { workNodeDao =>
      val workGraphStore = new WorkGraphStore(workNodeDao)
      testWith(workGraphStore)
    }

  def withWorkNodeDao[R](table: Table)(
    testWith: TestWith[WorkNodeDao, R]): R = {
    val workNodeDao = new WorkNodeDao(
      dynamoClient = dynamoClient,
      dynamoConfig = createDynamoConfigWith(table)
    )
    testWith(workNodeDao)
  }

  def sendWork(
    work: WorkStub,
    retriever: MemoryRetriever[WorkStub],
    queue: SQS.Queue
  ): Unit = {
    retriever.index ++= Map(work.id.toString -> work)
    sendNotificationToSQS(queue, body = work.id.toString)
  }

  def ciHash(ids: CanonicalId*): String =
    DigestUtils.sha256Hex(ids.sorted.map(_.underlying).mkString("+"))
}
