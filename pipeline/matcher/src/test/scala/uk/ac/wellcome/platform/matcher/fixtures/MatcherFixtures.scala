package uk.ac.wellcome.platform.matcher.fixtures

import org.apache.commons.codec.digest.DigestUtils
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.{Messaging, SQS}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{WorkGraphStore, WorkNodeDao}
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.fixtures.{LockingFixtures, S3}
import uk.ac.wellcome.storage.locking.DynamoLockingService

import scala.concurrent.ExecutionContext.Implicits.global

trait MatcherFixtures
    extends Messaging
    with LockingFixtures
    with LocalWorkGraphDynamoDb
    with S3 {

  def withLockTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedLocalDynamoDbTable(createLockTable) { table =>
      testWith(table)
    }

  def withWorkGraphTable[R](testWith: TestWith[Table, R]): R =
    withSpecifiedLocalDynamoDbTable(createWorkGraphTable) { table =>
      testWith(table)
    }

  def withWorkerService[R](queue: SQS.Queue, topic: Topic, graphTable: Table)(
    testWith: TestWith[MatcherWorkerService, R])(
    implicit objectStore: ObjectStore[TransformedBaseWork]): R =
    withSNSWriter(topic) { snsWriter =>
      withActorSystem { implicit actorSystem =>
        withMockMetricsSender { metricsSender =>
          withLockTable { lockTable =>
            withWorkGraphStore(graphTable) { workGraphStore =>
              withWorkMatcher(workGraphStore, lockTable, metricsSender) {
                workMatcher =>
                  withMessageStream[TransformedBaseWork, R](
                    queue = queue,
                    metricsSender = metricsSender
                  ) { messageStream =>
                    val workerService = new MatcherWorkerService(
                      messageStream = messageStream,
                      snsWriter = snsWriter,
                      workMatcher = workMatcher
                    )

                    workerService.run()

                    testWith(workerService)
                  }
              }
            }
          }
        }
      }
    }

  def withWorkerService[R](queue: SQS.Queue, topic: Topic)(
    testWith: TestWith[MatcherWorkerService, R])(
    implicit objectStore: ObjectStore[TransformedBaseWork]): R =
    withWorkGraphTable { graphTable =>
      withWorkerService(queue, topic, graphTable) { service =>
        testWith(service)
      }
    }

  def withWorkMatcher[R](
    workGraphStore: WorkGraphStore,
    lockTable: Table,
    metricsSender: MetricsSender)(testWith: TestWith[WorkMatcher, R]): R =
    withDynamoRowLockDao(lockTable) { dynamoRowLockDao =>
      withLockingService(dynamoRowLockDao, metricsSender) { lockingService =>
        val workMatcher = new WorkMatcher(
          workGraphStore = workGraphStore,
          lockingService = lockingService
        )
        testWith(workMatcher)
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
    testWith: TestWith[WorkNodeDao, R]): R = {
    val workNodeDao = new WorkNodeDao(
      dynamoDbClient,
      DynamoConfig(table = table.name, index = table.index)
    )
    testWith(workNodeDao)
  }

  def ciHash(str: String): String =
    DigestUtils.sha256Hex(str)
}
