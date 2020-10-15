package uk.ac.wellcome.platform.id_minter_works.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.Json
import io.circe.syntax._
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.id_minter.config.models.IdentifiersTableConfig
import uk.ac.wellcome.platform.id_minter.database.IdentifiersDao
import uk.ac.wellcome.platform.id_minter.models.IdentifiersTable
import uk.ac.wellcome.platform.id_minter.steps.IdentifierGenerator
import uk.ac.wellcome.platform.id_minter.fixtures.IdentifiersDatabase
import uk.ac.wellcome.platform.id_minter_works.services.IdMinterWorkerService
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.pipeline_storage.ElasticRetriever
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import WorkState.Denormalised

trait WorkerServiceFixture
    extends IdentifiersDatabase
    with BigMessagingFixture
    with ElasticsearchFixtures
    with Akka {
  def withWorkerService[R](
    messageSender: MemoryMessageSender = new MemoryMessageSender(),
    queue: Queue = Queue("url://q", "arn::q", visibilityTimeout = 1),
    identifiersDao: IdentifiersDao,
    identifiersTableConfig: IdentifiersTableConfig,
    index: Map[String, Json] = Map.empty)(
    testWith: TestWith[IdMinterWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { messageStream =>
        val identifierGenerator = new IdentifierGenerator(
          identifiersDao = identifiersDao
        )
        val workerService = new IdMinterWorkerService(
          identifierGenerator = identifierGenerator,
          sender = messageSender,
          messageStream = messageStream,
          jsonRetriever = new MemoryRetriever(index),
          rdsClientConfig = rdsClientConfig,
          identifiersTableConfig = identifiersTableConfig
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def withWorkerService[R](messageSender: MemoryMessageSender,
                           queue: Queue,
                           identifiersTableConfig: IdentifiersTableConfig,
                           index: Map[String, Json])(
    testWith: TestWith[IdMinterWorkerService[String], R]): R = {
    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton(
      s"jdbc:mysql://$host:$port",
      username,
      password,
      settings = ConnectionPoolSettings(maxSize = maxSize)
    )

    val identifiersDao = new IdentifiersDao(
      identifiers = new IdentifiersTable(
        identifiersTableConfig = identifiersTableConfig
      )
    )

    withWorkerService(
      messageSender,
      queue,
      identifiersDao,
      identifiersTableConfig,
      index) { service =>
      testWith(service)
    }
  }

  def withElasticStorageWorkerService[R](work: Work[Denormalised],
                                         queue: Queue,
                                         messageSender: MemoryMessageSender)(
    testWith: TestWith[IdMinterWorkerService[String], R]): R =
    withIdentifiersDatabase { identifiersTableConfig =>
      Class.forName("com.mysql.jdbc.Driver")
      ConnectionPool.singleton(
        s"jdbc:mysql://$host:$port",
        username,
        password,
        settings = ConnectionPoolSettings(maxSize = maxSize)
      )
      val identifiersDao = new IdentifiersDao(
        identifiers = new IdentifiersTable(
          identifiersTableConfig = identifiersTableConfig
        )
      )

      withActorSystem { implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) { messageStream =>
          withLocalDenormalisedWorksIndex { index =>
            insertIntoElasticsearch(index, work)

            val workerService = new IdMinterWorkerService(
              identifierGenerator = new IdentifierGenerator(
                identifiersDao = identifiersDao
              ),
              sender = messageSender,
              messageStream = messageStream,
              jsonRetriever = new ElasticRetriever(elasticClient, index),
              rdsClientConfig = rdsClientConfig,
              identifiersTableConfig = identifiersTableConfig
            )

            workerService.run()

            testWith(workerService)
          }
        }
      }
    }

  def createIndex(works: List[Work[Denormalised]]): Map[String, Json] =
    works.map(work => (work.id, work.asJson)).toMap
}
