package uk.ac.wellcome.platform.idminter.fixtures

import io.circe.Json
import scalikejdbc.{ConnectionPool, DB}
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.config.models.IdentifiersTableConfig
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.MemoryStore
import uk.ac.wellcome.storage.streaming.Codec._

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends IdentifiersDatabase
    with BigMessagingFixture {
  def withWorkerService[R](bucket: Bucket,
                           topic: Topic,
                           queue: Queue,
                           identifiersDao: IdentifiersDao,
                           identifiersTableConfig: IdentifiersTableConfig)(
    testWith: TestWith[IdMinterWorkerService[SNSConfig], R]): R =
    withActorSystem { implicit actorSystem =>
      withSqsBigMessageSender[Json, R](bucket, topic) { bigMessageSender =>
        {
          implicit val typedStoreT =
            MemoryTypedStoreCompanion[ObjectLocation, Json]()
          withBigMessageStream[Json, R](queue) { messageStream =>
            val workerService = new IdMinterWorkerService(
              idEmbedder = new IdEmbedder(
                identifierGenerator = new IdentifierGenerator(
                  identifiersDao = identifiersDao
                )
              ),
              sender = bigMessageSender,
              messageStream = messageStream,
              rdsClientConfig = rdsClientConfig,
              identifiersTableConfig = identifiersTableConfig
            )

            workerService.run()

            testWith(workerService)
          }
        }
      }
    }

  def withWorkerService[R](bucket: Bucket,
                           topic: Topic,
                           queue: Queue,
                           identifiersTableConfig: IdentifiersTableConfig)(
    testWith: TestWith[IdMinterWorkerService[SNSConfig], R]): R = {
    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton(s"jdbc:mysql://$host:$port", username, password)

    val memoryStore = new MemoryStore[SourceIdentifier, Identifier](Map.empty)

    val identifiersDao = new IdentifiersDao(
      db = DB.connect(),
      identifiers = new IdentifiersTable(
        identifiersTableConfig = identifiersTableConfig
      ),
      memoryStore
    )
    withWorkerService(
      bucket,
      topic,
      queue,
      identifiersDao,
      identifiersTableConfig) { service =>
      testWith(service)
    }
  }
}
