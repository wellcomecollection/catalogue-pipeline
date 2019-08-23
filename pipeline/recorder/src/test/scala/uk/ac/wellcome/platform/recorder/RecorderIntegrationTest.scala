package uk.ac.wellcome.platform.recorder

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}

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

  it("saves received works to VHS, and puts the S3 location on the queue") {
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
                val location = vhs.getLocation(key)
                location.isSuccess shouldBe true
                getObjectFromS3[TransformedBaseWork](location.get) shouldBe work
                msgSender.getMessages[ObjectLocation].toList shouldBe
                  List(location.get)
              }
            }
          }
        }
      }
    }
  }
}
