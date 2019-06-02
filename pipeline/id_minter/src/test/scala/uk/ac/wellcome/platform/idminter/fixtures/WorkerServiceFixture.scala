package uk.ac.wellcome.platform.idminter.fixtures

import io.circe.Json
import scalikejdbc.{ConnectionPool, DB}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.platform.idminter.config.models.IdentifiersTableConfig
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.IdentifiersTable
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.storage.streaming.CodecInstances._

trait WorkerServiceFixture extends IdentifiersDatabase with Messaging {
  def withWorkerService[R](bucket: Bucket,
                           messageSender: MemoryBigMessageSender[Json],
                           queue: Queue,
                           identifiersDao: IdentifiersDao,
                           identifiersTableConfig: IdentifiersTableConfig)(
    testWith: TestWith[IdMinterWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withMetricsSender() { metricsSender =>
        withMessageStream[Json, R](queue, metricsSender) { messageStream =>
          val workerService = new IdMinterWorkerService(
            idEmbedder = new IdEmbedder(
              identifierGenerator = new IdentifierGenerator(
                identifiersDao = identifiersDao
              )
            ),
            messageSender = messageSender,
            messageStream = messageStream,
            rdsClientConfig = rdsClientConfig,
            identifiersTableConfig = identifiersTableConfig
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }

  def withWorkerService[R](bucket: Bucket,
                           messageSender: MemoryBigMessageSender[Json],
                           queue: Queue,
                           identifiersTableConfig: IdentifiersTableConfig)(
    testWith: TestWith[IdMinterWorkerService[String], R]): R = {
    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton(s"jdbc:mysql://$host:$port", username, password)

    val identifiersDao = new IdentifiersDao(
      db = DB.connect(),
      identifiers = new IdentifiersTable(
        identifiersTableConfig = identifiersTableConfig
      )
    )
    withWorkerService(
      bucket,
      messageSender,
      queue,
      identifiersDao,
      identifiersTableConfig) { service =>
      testWith(service)
    }
  }
}
