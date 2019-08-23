package uk.ac.wellcome.platform.recorder

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.typesafe.{VHSBuilder, EmptyMetadata}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.{Identified, ObjectLocation, ObjectLocationPrefix}
import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  TypedStoreEntry
}

class RecorderIntegrationTest
    extends FunSpec
    with Matchers
    with IntegrationPatience
    with DynamoFixtures
    with BigMessagingFixture
    with WorkerServiceFixture
    with WorksGenerators {

  override def createTable(
    table: DynamoFixtures.Table): DynamoFixtures.Table = {
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S
    )
  }

  it("receives a transformed Work, saves it to the VHS, and sends off a message") {
    withLocalSqsQueue { queue =>
      withLocalS3Bucket { bucket =>
        withLocalDynamoDbTable { table =>
          withMemoryMessageSender { msgSender =>
            val vhs = VHSBuilder.build[TransformedBaseWork](
              ObjectLocationPrefix(namespace = bucket.name, path = "recorder"),
              DynamoConfig(table.name, table.index),
              dynamoClient,
              s3Client,
            )
            withWorkerService(queue, vhs, msgSender) { service =>
              val work = createUnidentifiedWork
              sendMessage[TransformedBaseWork](queue = queue, obj = work)
              eventually {
                val key = assertWorkStored(vhs, work)
                val tryLocation = vhs.getLocation(key)
                tryLocation.isSuccess shouldBe true
                val location = tryLocation.get

                // Check index entry stored correctly in dynamo
                vhs.hybridStore.indexedStore.get(key) shouldBe
                  Right(
                    Identified(
                      key,
                      HybridIndexedStoreEntry(location, EmptyMetadata())))

                // Check typed entry stored correctly in S3
                vhs.hybridStore.typedStore.get(location) shouldBe
                  Right(
                    Identified(
                      location,
                      TypedStoreEntry(work, Map.empty)))

                // Check S3 location put on queue
                msgSender.getMessages[ObjectLocation].toList shouldBe
                  List(location)
              }
            }
          }
        }
      }
    }
  }
}
