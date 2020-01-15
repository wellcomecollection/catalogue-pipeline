package uk.ac.wellcome.platform.idminter.fixtures

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import io.circe.Json
import org.scanamo.DynamoFormat
import scalikejdbc.{ConnectionPool, DB}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}
import uk.ac.wellcome.platform.idminter.config.models.IdentifiersTableConfig
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
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
    extends IdentifiersDatabase
    with BigMessagingFixture
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
    identifiersDao: IdentifiersDao[StoreType],
    identifiersTableConfig: IdentifiersTableConfig)(
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
                           identifiersTableConfig: IdentifiersTableConfig,
  )(testWith: TestWith[IdMinterWorkerService[_, SNSConfig], R]): R = {
    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton(s"jdbc:mysql://$host:$port", username, password)

    withLocalDynamoDbTable { table =>

      implicit val format: DynamoFormat[SourceIdentifier] = DynamoFormat
        .coercedXmap[SourceIdentifier, String, IllegalArgumentException](
          _.split("/", 2) match {
            case Array(identifierType, value) =>
              SourceIdentifier(
                IdentifierType(identifierType),
                "not_a_thing",
                value
              )

            case _ =>
              throw new IllegalArgumentException(
                s"Cannot create bag ID from $value")
          }
        )(
          _.toString
        )
      // TODO: SourceIdentifier is not sufficient to uniquely id things (needs ontologyType)
      // Deal with slashes in identifier ontology type & id type must not have
      val dynamoStore = new SimpleDynamoStore[SourceIdentifier, Identifier](
        DynamoConfig(table.name, table.index)
      )

      val identifiersDao = new IdentifiersDao(
        db = DB.connect(),
        identifiers = new IdentifiersTable(
          identifiersTableConfig = identifiersTableConfig
        ),
        dynamoStore
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
}
