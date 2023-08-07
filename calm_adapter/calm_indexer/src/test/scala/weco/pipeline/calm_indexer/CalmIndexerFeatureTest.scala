package weco.pipeline.calm_indexer

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.catalogue.source_model.Implicits._
import weco.elasticsearch.IndexConfig
import weco.pipeline.calm_indexer.fixtures.IndexerFixtures
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

class CalmIndexerFeatureTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with CalmRecordGenerators
    with S3ObjectLocationGenerators
    with IndexerFixtures {
  it("indexes Calm records") {
    val record = createCalmRecordWith(
      ("Modified", "29/06/2020"),
      ("Document", "")
    )

    val location = createS3ObjectLocation

    val payload =
      CalmSourcePayload(id = record.id, location = location, version = 1)

    val store = MemoryTypedStore[S3ObjectLocation, CalmRecord](
      initialEntries = Map(location -> record)
    )

    withLocalElasticsearchIndex(config = IndexConfig.empty) { index =>
      withLocalSqsQueue() { queue =>
        withWorker(queue, store, index) { _ =>
          sendNotificationToSQS(queue, payload)

          assertElasticsearchEventuallyHas(
            index = index,
            id = record.id,
            json = s"""
                 |{
                 |  "Modified": "29/06/2020"
                 |}
                 |""".stripMargin
          )
        }
      }
    }
  }

  it("removes a deleted Calm record") {
    val record = createCalmRecordWith(
      ("Modified", "29/06/2020"),
      ("Document", "")
    )

    val location = createS3ObjectLocation

    val payload =
      CalmSourcePayload(id = record.id, location = location, version = 1)

    val payloadDeleted =
      payload.copy(version = 2, isDeleted = true)

    val store = MemoryTypedStore[S3ObjectLocation, CalmRecord](
      initialEntries = Map(location -> record)
    )

    withLocalElasticsearchIndex(config = IndexConfig.empty) { index =>
      withLocalSqsQueue() { queue =>
        withWorker(queue, store, index) { _ =>
          sendNotificationToSQS(queue, payload)

          assertElasticsearchEventuallyHas(
            index = index,
            id = record.id,
            json = s"""
                      |{
                      |  "Modified": "29/06/2020"
                      |}
                      |""".stripMargin
          )

          sendNotificationToSQS(queue, payloadDeleted)

          assertElasticsearchEmpty(index)
        }
      }
    }
  }
}
