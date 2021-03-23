package uk.ac.wellcome.platform.matcher.fixtures

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
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, WorkNode}
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.models.WorkLinks
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{WorkGraphStore, WorkNodeDao}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table
import uk.ac.wellcome.storage.locking.dynamo.{
  DynamoLockDaoFixtures,
  DynamoLockingService,
  ExpiringLock
}
import uk.ac.wellcome.storage.locking.memory.{
  MemoryLockDao,
  MemoryLockingService
}

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
    retriever.index ++= Map(links.workId -> links)
    sendNotificationToSQS(queue, body = links.workId)
  }

  def ciHash(ids: String*): String =
    DigestUtils.sha256Hex(ids.sorted.mkString("+"))

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
