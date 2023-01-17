package weco.pipeline.sierra_indexer.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Indexes
import org.scalatest.funspec.AnyFunSpec
import weco.messaging.fixtures.SQS.QueuePair
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.pipeline.sierra_indexer.fixtures.IndexerFixtures

class WorkerTest
    extends AnyFunSpec
    with IndexerFixtures
    with S3ObjectLocationGenerators
    with SierraRecordGenerators {
  it("returns an error if one of the bulk requests fails") {
    withIndices { indexPrefix =>
      val location = createS3ObjectLocation

      val bibId = createSierraBibNumber

      val transformable = createSierraTransformableWith(
        bibRecord = createSierraBibRecordWith(
          id = bibId,
          data = s"""
                 |{
                 |  "id" : "$bibId",
                 |  "updatedDate" : "2013-12-12T13:56:07Z",
                 |  "deleted" : false,
                 |  "varFields" : [
                 |    {
                 |      "fieldTag" : "b",
                 |      "content" : "22501328220"
                 |    },
                 |    {
                 |      "fieldTag" : "c",
                 |      "marcTag" : "949",
                 |      "ind1" : " ",
                 |      "ind2" : " ",
                 |      "subfields" : [
                 |        {
                 |          "tag" : "a",
                 |          "content" : "/RHO"
                 |        }
                 |      ]
                 |    }
                 |  ]
                 |}
                 |""".stripMargin
        )
      )

      val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
        initialEntries = Map(location -> transformable)
      )

      // Make the varfields index read-only, so any attempt to index data into
      // this index should fail.
      elasticClient
        .execute(
          updateSettings(
            Indexes(s"${indexPrefix}_varfields"),
            settings = Map("blocks.read_only" -> "true")
          )
        )
        .await

      // TODO: This should be a regular queue, not a DLQ pair
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, store, indexPrefix) { worker =>
            val future = worker.processMessage(
              createNotificationMessageWith(
                SierraSourcePayload(
                  id = bibId.withoutCheckDigit,
                  location = location,
                  version = 1
                )
              )
            )

            whenReady(future.failed) { err =>
              err shouldBe a[RuntimeException]
              err.getMessage should startWith("Errors in the bulk response")
            }
          }
      }
    }
  }

  it("uses a strict mapping for varfields") {
    withIndices { indexPrefix =>
      val location = createS3ObjectLocation

      val bibId = createSierraBibNumber

      // This example is quite carefully constructed: in the first version
      // of the Sierra indexer, we didn't have any mappings, and ES guessed
      // that one of the fields was a date -- preventing any non-date data
      // being indexed in future updates.
      val transformable = createSierraTransformableWith(
        bibRecord = createSierraBibRecordWith(
          id = bibId,
          data = s"""
                 |{
                 |  "id" : "$bibId",
                 |  "updatedDate" : "2013-12-12T13:56:07Z",
                 |  "deleted" : false,
                 |  "varFields" : [
                 |    {
                 |      "fieldTag" : "a",
                 |      "content" : "2021-05-24"
                 |    },
                 |    {
                 |      "fieldTag" : "b",
                 |      "content": "This isn't a date"
                 |    }
                 |  ]
                 |}
                 |""".stripMargin
        )
      )

      val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
        initialEntries = Map(location -> transformable)
      )

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, store, indexPrefix) { _ =>
            sendNotificationToSQS(
              queue,
              SierraSourcePayload(
                id = bibId.withoutCheckDigit,
                location = location,
                version = 1
              )
            )

            eventually {
              elasticClient
                .execute(
                  count(Indexes(s"${indexPrefix}_varfields"))
                )
                .await
                .result
                .count shouldBe 2

              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
      }
    }
  }

  it("uses a strict mapping for fixed fields") {
    withIndices { indexPrefix =>
      val location = createS3ObjectLocation

      val bibId = createSierraBibNumber

      // This example is quite carefully constructed: in the first version
      // of the Sierra indexer, we didn't have any mappings, and ES guessed
      // that one of the fields was a date -- preventing any non-date data
      // being indexed in future updates.
      val transformable = createSierraTransformableWith(
        bibRecord = createSierraBibRecordWith(
          id = bibId,
          data = s"""
                 |{
                 |  "id" : "$bibId",
                 |  "updatedDate" : "2013-12-12T13:56:07Z",
                 |  "deleted" : false,
                 |  "fixedFields" : {
                 |    "1": {
                 |      "label": "ONE",
                 |      "value": "2021-05-24"
                 |    },
                 |    "2": {
                 |      "label": "TWO",
                 |      "value": "This is the second field"
                 |    }
                 |  }
                 |}
                 |""".stripMargin
        )
      )

      val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
        initialEntries = Map(location -> transformable)
      )

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, store, indexPrefix) { _ =>
            sendNotificationToSQS(
              queue,
              SierraSourcePayload(
                id = bibId.withoutCheckDigit,
                location = location,
                version = 1
              )
            )

            eventually {
              elasticClient
                .execute(
                  count(Indexes(s"${indexPrefix}_fixedfields"))
                )
                .await
                .result
                .count shouldBe 2

              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
      }
    }
  }
}
