package weco.pipeline.matcher.fixtures

import org.apache.commons.codec.digest.DigestUtils
import org.scanamo.generic.semiauto.deriveDynamoFormat
import org.scanamo.query.UniqueKey
import org.scanamo.{
  DynamoFormat,
  DynamoReadError,
  Scanamo,
  Table => ScanamoTable
}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.matcher.{MatchedIdentifiers, WorkNode}
import weco.storage.fixtures.DynamoFixtures.Table
import weco.storage.locking.dynamo.{
  DynamoLockDaoFixtures,
  DynamoLockingService,
  ExpiringLock
}
import weco.storage.locking.memory.{MemoryLockDao, MemoryLockingService}
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.models.WorkLinks
import weco.pipeline.matcher.services.MatcherWorkerService
import weco.pipeline.matcher.storage.{WorkGraphStore, WorkNodeDao}
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.MemoryRetriever

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
    workLinksRetriever: MemoryRetriever[WorkLinks],
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
                workLinksRetriever = workLinksRetriever,
                msgStream,
                messageSender,
                workMatcher)
            workerService.run()
            testWith(workerService)
          }
        }
      }
    }

  def withWorkerService[R](workLinksRetriever: MemoryRetriever[WorkLinks],
                           queue: SQS.Queue,
                           messageSender: MemoryMessageSender)(
    testWith: TestWith[MatcherWorkerService[String], R]): R =
    withWorkGraphTable { graphTable =>
      withWorkerService(workLinksRetriever, queue, messageSender, graphTable) {
        service =>
          testWith(service)
      }
    }

  def withWorkMatcher[R](workGraphStore: WorkGraphStore)(
    testWith: TestWith[WorkMatcher, R]): R = {
    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID]
    val lockingService =
      new MemoryLockingService[Set[MatchedIdentifiers], Future]()

    val workMatcher = new WorkMatcher(
      workGraphStore = workGraphStore,
      lockingService = lockingService
    )

    testWith(workMatcher)
  }

  def withWorkMatcherAndLockingService[R](
    workGraphStore: WorkGraphStore,
    lockingService: DynamoLockingService[Set[MatchedIdentifiers], Future])(
    testWith: TestWith[WorkMatcher, R]): R = {
    val workMatcher = new WorkMatcher(workGraphStore, lockingService)
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
    links: WorkLinks,
    retriever: MemoryRetriever[WorkLinks],
    queue: SQS.Queue
  ): Any = {
    retriever.index ++= Map(links.workId.toString -> links)
    sendNotificationToSQS(queue, body = links.workId.toString)
  }

  def ciHash(ids: CanonicalId*): String =
    DigestUtils.sha256Hex(ids.sorted.map(_.underlying).mkString("+"))

  def get[T](dynamoClient: DynamoDbClient, tableName: String)(
    key: UniqueKey[_])(
    implicit format: DynamoFormat[T]): Option[Either[DynamoReadError, T]] =
    Scanamo(dynamoClient).exec { ScanamoTable[T](tableName).get(key) }

  def put[T](dynamoClient: DynamoDbClient, tableName: String)(obj: T)(
    implicit format: DynamoFormat[T]) =
    Scanamo(dynamoClient).exec { ScanamoTable[T](tableName).put(obj) }

  def scan[T](dynamoClient: DynamoDbClient, tableName: String)(
    implicit format: DynamoFormat[T]): List[Either[DynamoReadError, T]] =
    Scanamo(dynamoClient).exec { ScanamoTable[T](tableName).scan() }
}
