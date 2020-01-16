package uk.ac.wellcome.platform.idminter.fixtures

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import io.circe.Json
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.services.{IdMinterWorkerService, IdentifiersService}
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
import uk.ac.wellcome.platform.idminter.utils.DynamoFormats._
import uk.ac.wellcome.platform.idminter.utils.SimpleDynamoStore
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.streaming.Codec._

import scala.concurrent.ExecutionContext.Implicits.global
import org.scanamo.auto._

trait WorkerServiceFixture
    extends BigMessagingFixture
    with DynamoFixtures {

  override def createTable(
    table: DynamoFixtures.Table): DynamoFixtures.Table = {
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S
    )
  }

  def withWorkerService[R, StoreType <: Store[SourceIdentifier, Identifier]](
    bucket: Bucket,
    topic: Topic,
    queue: Queue,
    identifiersDao: IdentifiersService[StoreType])(
    testWith: TestWith[IdMinterWorkerService[_, SNSConfig], R]): R =
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
              messageStream = messageStream
            )

            workerService.run()

            testWith(workerService)
          }
        }
      }
    }

  def withWorkerService[R](bucket: Bucket,
                           topic: Topic,
                           queue: Queue
  )(testWith: TestWith[IdMinterWorkerService[_, SNSConfig], R]): R = {
    withLocalDynamoDbTable { table =>

      // TODO: Deal with slashes in identifier ontology type & id type must not have
      val dynamoStore = new SimpleDynamoStore[SourceIdentifier, Identifier](
        DynamoConfig(table.name, table.index)
      )

      val identifiersDao = new IdentifiersService(dynamoStore)

      withWorkerService(
        bucket,
        topic,
        queue,
        identifiersDao) { service =>
        testWith(service)
      }
    }
  }
}
