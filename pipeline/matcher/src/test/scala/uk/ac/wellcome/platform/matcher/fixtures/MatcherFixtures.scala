package uk.ac.wellcome.platform.matcher.fixtures

import java.util.UUID

import com.amazonaws.services.cloudwatch.model.StandardUnit
import org.apache.commons.codec.digest.DigestUtils
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{Messaging, SQS}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.matcher.MatchedIdentifiers
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{
  DynamoWorkNodeDao,
  WorkGraphStore
}
import uk.ac.wellcome.storage.{LockDao, LockingService, ObjectStore}
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.fixtures.{LockingFixtures, S3}
import uk.ac.wellcome.storage.locking.DynamoLockingService
import uk.ac.wellcome.storage.memory.MemoryLockDao

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MatcherFixtures
    extends Messaging
    with LocalWorkGraphDynamoDb
    with LockingFixtures
    with S3 {

  def withLockTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedLocalDynamoDbTable(createLockTable) { table =>
      testWith(table)
    }

  def withWorkGraphTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedLocalDynamoDbTable(createWorkGraphTable) { table =>
      testWith(table)
    }

  def withWorkerService[R](
    queue: Queue,
    messageSender: MemoryMessageSender,
    graphTable: Table)(testWith: TestWith[MatcherWorkerService[String], R])(
    implicit objectStore: ObjectStore[TransformedBaseWork]): R =
    withActorSystem { implicit actorSystem =>
      val metrics = new MemoryMetrics[StandardUnit]()
      withLockTable { lockTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
            withMessageStream[TransformedBaseWork, R](queue, metrics) {
              messageStream =>
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

  def withWorkerService[R](queue: Queue, messageSender: MemoryMessageSender)(
    testWith: TestWith[MatcherWorkerService[String], R])(
    implicit objectStore: ObjectStore[TransformedBaseWork]): R =
    withWorkGraphTable { graphTable =>
      withWorkerService(queue, messageSender, graphTable) { service =>
        testWith(service)
      }
    }

  def withWorkMatcherAndLockingService[R](workGraphStore: WorkGraphStore,
                                          lockingService: DynamoLockingService)(
    testWith: TestWith[WorkMatcher, R]): R = {
    val workMatcher = new WorkMatcher(workGraphStore, lockingService)
    testWith(workMatcher)
  }

  def withWorkGraphStore[R](graphTable: Table)(
    testWith: TestWith[WorkGraphStore, R]): R = {
    withWorkNodeDao(graphTable) { workNodeDao =>
      val workGraphStore = new WorkGraphStore(workNodeDao)
      testWith(workGraphStore)
    }
  }

  def withWorkNodeDao[R](table: Table)(
    testWith: TestWith[DynamoWorkNodeDao, R]): R = {
    val workNodeDao = new DynamoWorkNodeDao(
      dynamoDbClient,
      DynamoConfig(table = table.name, index = table.index)
    )
    testWith(workNodeDao)
  }

  def ciHash(str: String): String =
    DigestUtils.sha256Hex(str)
}
