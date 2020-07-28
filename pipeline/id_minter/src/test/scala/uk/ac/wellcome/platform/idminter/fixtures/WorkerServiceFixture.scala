package uk.ac.wellcome.platform.idminter.fixtures

import io.circe.Json
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.idminter.config.models.IdentifiersTableConfig
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.IdentifiersTable
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.IdentifierGenerator

trait WorkerServiceFixture
    extends IdentifiersDatabase
    with BigMessagingFixture {
  def withWorkerService[R](
    messageSender: MemoryMessageSender = new MemoryMessageSender(),
    queue: Queue = Queue("url://q", "arn::q", visibilityTimeout = 1),
    identifiersDao: IdentifiersDao,
    identifiersTableConfig: IdentifiersTableConfig)(
    testWith: TestWith[IdMinterWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withBigMessageStream[Json, R](queue) { messageStream =>
        val identifierGenerator = new IdentifierGenerator(
          identifiersDao = identifiersDao
        )
        val workerService = new IdMinterWorkerService(
          identifierGenerator = identifierGenerator,
          sender = messageSender,
          messageStream = messageStream,
          rdsClientConfig = rdsClientConfig,
          identifiersTableConfig = identifiersTableConfig
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def withWorkerService[R](messageSender: MemoryMessageSender,
                           queue: Queue,
                           identifiersTableConfig: IdentifiersTableConfig)(
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
      identifiersTableConfig) { service =>
      testWith(service)
    }
  }
}
