package weco.catalogue.sierra_indexer.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Indexes
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.sierra_indexer.fixtures.IndexerFixtures
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.generators.SierraGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra.SierraTransformable

class WorkerTest extends AnyFunSpec with IndexerFixtures with S3ObjectLocationGenerators with SierraGenerators {
  it("returns an error if one of the bulk requests fails") {
    withIndices { indexPrefix =>
      val location = createS3ObjectLocation

      val bibId = createSierraBibNumber

      val transformable = createSierraTransformableWith(
        maybeBibRecord = Some(
          createSierraBibRecordWith(
            id = bibId,
            data =
              s"""
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
      )

      val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
        initialEntries = Map(location -> transformable)
      )

      // Make the varfields index read-only, so any attempt to index data into
      // this index should fail.
      elasticClient.execute(
        updateSettings(
          Indexes(s"${indexPrefix}_varfields"), settings = Map("blocks.read_only" -> "true")
        )
      ).await

      withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
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
}
