package uk.ac.wellcome.platform.matcher.fixtures

import java.util.UUID

import org.apache.commons.codec.digest.DigestUtils
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.fixtures.{Messaging, SQS}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.matcher.MatchedIdentifiers
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{DynamoWorkNodeDao, WorkGraphStore, WorkNodeDao}
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.memory.MemoryLockDao
import uk.ac.wellcome.storage.{LockDao, LockingService, ObjectStore}

import scala.util.{Random, Try}

trait MatcherFixtures extends Messaging with LocalWorkGraphDynamoDb {

  type DestinationConfig = String

  type MatcherLockDao = LockDao[String, UUID]
  type MatcherLockingService = LockingService[Set[MatchedIdentifiers], Try, MatcherLockDao]

  def createLockDao: MemoryLockDao[String, UUID] =
    new MemoryLockDao[String, UUID] {}

  def createLockingService(dao: MatcherLockDao = createLockDao): MatcherLockingService = new MatcherLockingService {
    override implicit val lockDao: MatcherLockDao = dao

    override protected def createContextId(): lockDao.ContextId = UUID.randomUUID()
  }

  def createWorkMatcher(
    graphTable: Table,
    lockingService: MatcherLockingService = createLockingService()
  ): WorkMatcher =
    withWorkGraphStore(graphTable) { workGraphStore =>
      new WorkMatcher(
        workGraphStore = workGraphStore,
        lockingService = lockingService
      )
    }

  def withLockTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedLocalDynamoDbTable(createLockTable) { table =>
      testWith(table)
    }

  def withWorkGraphTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedLocalDynamoDbTable(createWorkGraphTable) { table =>
      testWith(table)
    }

  def createMessageSender: MemoryMessageSender =
    new MemoryMessageSender(
      destination = Random.alphanumeric.take(10) mkString,
      subject = Random.alphanumeric.take(10) mkString
    )

  def withWorkerService[R](queue: SQS.Queue, messageSender: MessageSender[DestinationConfig], graphTable: Table)(
    testWith: TestWith[MatcherWorkerService[DestinationConfig], R])(
    implicit objectStore: ObjectStore[TransformedBaseWork]): R =
    withActorSystem { implicit actorSystem =>
      withMockMetricsSender { metricsSender =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val lockDao = createLockDao
          withWorkMatcher(workGraphStore, lockDao) { workMatcher =>
            withMessageStream[TransformedBaseWork, R](
              queue = queue,
              metricsSender = metricsSender
            ) { messageStream =>
              val workerService = new MatcherWorkerService(
                messageStream = messageStream,
                messageSender = messageSender,
                workMatcher = workMatcher
              )

              workerService.run()

              testWith(workerService)
            }
          }
        }
      }
    }

  def withWorkerService[R](queue: SQS.Queue, messageSender: MessageSender[DestinationConfig])(
    testWith: TestWith[MatcherWorkerService[DestinationConfig], R])(
    implicit objectStore: ObjectStore[TransformedBaseWork]): R =
    withWorkGraphTable { graphTable =>
      withWorkerService(queue, messageSender, graphTable) { service =>
        testWith(service)
      }
    }

  def withWorkMatcher[R](workGraphStore: WorkGraphStore, dao: MatcherLockDao)(
    testWith: TestWith[WorkMatcher, R]): R = {
    val lockingService = createLockingService(dao)

    withWorkMatcherAndLockingService(workGraphStore, lockingService) {
      testWith
    }
  }


  def withWorkMatcherAndLockingService[R](workGraphStore: WorkGraphStore,
                                          lockingService: MatcherLockingService)(
    testWith: TestWith[WorkMatcher, R]): R = {
    val workMatcher = new WorkMatcher(workGraphStore, lockingService)
    testWith(workMatcher)
  }

  def withWorkGraphStore[R](testWith: TestWith[WorkGraphStore, R]): R =
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { graphStore =>
        testWith(graphStore)
      }
    }

  def withWorkGraphStore[R](graphTable: Table)(
    testWith: TestWith[WorkGraphStore, R]): R = {
    withWorkNodeDao(graphTable) { workNodeDao =>
      val workGraphStore = new WorkGraphStore(workNodeDao)
      testWith(workGraphStore)
    }
  }

  def withWorkNodeDao[R](testWith: TestWith[WorkNodeDao, R]): R =
    withWorkGraphTable { table =>
      withWorkNodeDao(table) { workNodeDao =>
        testWith(workNodeDao)
      }
    }

  def withWorkNodeDao[R](table: Table)(
    testWith: TestWith[WorkNodeDao, R]): R = {
    val workNodeDao = new DynamoWorkNodeDao(
      dynamoDbClient = dynamoDbClient,
      dynamoConfig = DynamoConfig(table = table.name, index = table.index)
    )
    testWith(workNodeDao)
  }

  def ciHash(str: String): String =
    DigestUtils.sha256Hex(str)
}
