package weco.catalogue.sierra_indexer

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Indexes}
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.sierra_indexer.fixtures.IndexerFixtures
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.generators.SierraGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra.{
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord,
  SierraTransformable
}

import java.time.Instant

class SierraIndexerFeatureTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with SierraGenerators
    with S3ObjectLocationGenerators
    with IndexerFixtures {
  it("indexes bib records and their varFields/fixedFields") {
    val location = createS3ObjectLocation

    val bibId = createSierraBibNumber

    val itemIds = (1 to 5).map { _ =>
      createSierraItemNumber
    }
    val holdingsIds = (1 to 4).map { _ =>
      createSierraHoldingsNumber
    }
    val orderIds = (1 to 4).map { _ =>
      createSierraOrderNumber
    }

    val transformable = createSierraTransformableWith(
      maybeBibRecord = Some(
        createSierraBibRecordWith(
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
               |  ],
               |  "fixedFields": {
               |    "86": {
               |      "label" : "AGENCY",
               |       "value" : "1"
               |    },
               |    "265": {
               |      "label" : "Inherit Location",
               |      "value" : false
               |    }
               |  }
               |}
               |""".stripMargin
        )
      ),
      itemRecords = itemIds.map { id =>
        createSierraItemRecordWith(id = id)
      },
      holdingsRecords = holdingsIds.map { id =>
        createSierraHoldingsRecordWith(id = id)
      },
      orderRecords = orderIds.map { id =>
        createSierraOrderRecordWith(id = id)
      }
    )

    val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
      initialEntries = Map(location -> transformable)
    )

    withIndices { indexPrefix =>
      withLocalSqsQueue() { queue =>
        withWorker(queue, store, indexPrefix) { _ =>
          sendNotificationToSQS(
            queue,
            SierraSourcePayload(
              id = bibId.withoutCheckDigit,
              location = location,
              version = 1)
          )

          val itemIdsList =
            itemIds
              .map { id =>
                s"""
                 |"${id.withoutCheckDigit}"
                 |""".stripMargin
              }
              .sorted
              .mkString(",")

          val holdingsIdsList =
            holdingsIds
              .map { id =>
                s"""
                 |"${id.withoutCheckDigit}"
                 |""".stripMargin
              }
              .sorted
              .mkString(",")

          val orderIdsList =
            orderIds
              .map { id =>
                s"""
                   |"${id.withoutCheckDigit}"
                   |""".stripMargin
              }
              .sorted
              .mkString(",")

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_bibs"),
            id = bibId.withoutCheckDigit,
            json = s"""
                |{
                |  "id" : "$bibId",
                |  "idWithCheckDigit": "${bibId.withCheckDigit}",
                |  "updatedDate" : "2013-12-12T13:56:07Z",
                |  "deleted" : false,
                |  "itemIds": [$itemIdsList],
                |  "holdingsIds": [$holdingsIdsList],
                |  "orderIds": [$orderIdsList]
                |}
                |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"bibs-${bibId.withoutCheckDigit}-0",
            json = s"""
                |{
                |  "parent": {
                |    "recordType": "bibs",
                |    "id": "${bibId.withoutCheckDigit}",
                |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                |  },
                |  "position": 0,
                |  "varField": {
                |    "fieldTag" : "b",
                |    "content" : "22501328220"
                |  }
                |}
                |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"bibs-${bibId.withoutCheckDigit}-1",
            json = s"""
                |{
                |  "parent": {
                |    "recordType": "bibs",
                |    "id": "${bibId.withoutCheckDigit}",
                |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                |  },
                |  "position": 1,
                |  "varField": {
                |    "fieldTag" : "c",
                |    "marcTag" : "949",
                |    "ind1" : " ",
                |    "ind2" : " ",
                |    "subfields" : [
                |      {
                |        "tag" : "a",
                |        "content" : "/RHO"
                |      }
                |    ]
                |  }
                |}
                |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"bibs-${bibId.withoutCheckDigit}-86",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "bibs",
                      |    "id": "${bibId.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                      |  },
                      |  "code": "86",
                      |  "fixedField": {
                      |    "label" : "AGENCY",
                      |    "value" : "1"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"bibs-${bibId.withoutCheckDigit}-265",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "bibs",
                      |    "id": "${bibId.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                      |  },
                      |  "code": "265",
                      |  "fixedField": {
                      |    "label" : "Inherit Location",
                      |    "value" : false
                      |  }
                      |}
                      |""".stripMargin
          )
        }
      }
    }
  }

  it("replaces a bib record that has changed") {
    val location1 = createS3ObjectLocation
    val location2 = createS3ObjectLocation

    val bibId = createSierraBibNumber

    val transformable1 = createSierraTransformableWith(
      maybeBibRecord = Some(
        createSierraBibRecordWith(
          id = bibId,
          data = s"""
                    |{
                    |  "id" : "$bibId",
                    |  "updatedDate" : "2001-01-01T01:01:01Z",
                    |  "deleted" : false,
                    |  "varFields" : [
                    |    {
                    |      "fieldTag" : "b",
                    |      "content" : "11111111"
                    |    }
                    |  ],
                    |  "fixedFields": {
                    |    "86": {
                    |      "label" : "AGENCY",
                    |       "value" : "1"
                    |    }
                    |  }
                    |}
                    |""".stripMargin
        )
      ),
      itemRecords = List(),
      holdingsRecords = List(),
    )

    val transformable2 = createSierraTransformableWith(
      maybeBibRecord = Some(
        createSierraBibRecordWith(
          id = bibId,
          data = s"""
                    |{
                    |  "id" : "$bibId",
                    |  "updatedDate" : "2002-02-02T02:02:02Z",
                    |  "deleted" : false,
                    |  "varFields" : [
                    |    {
                    |      "fieldTag" : "b",
                    |      "content" : "22222222"
                    |    }
                    |  ],
                    |  "fixedFields": {
                    |    "86": {
                    |      "label" : "AGENCY",
                    |       "value" : "2"
                    |    }
                    |  }
                    |}
                    |""".stripMargin
        )
      ),
      itemRecords = List(),
      holdingsRecords = List(),
    )

    val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
      initialEntries = Map(
        location1 -> transformable1,
        location2 -> transformable2
      )
    )

    withIndices { indexPrefix =>
      withLocalSqsQueuePair(visibilityTimeout = 5) {
        case QueuePair(queue, dlq) =>
          withWorker(queue, store, indexPrefix) { _ =>
            sendNotificationToSQS(
              queue,
              SierraSourcePayload(
                id = bibId.withoutCheckDigit,
                location = location1,
                version = 1)
            )

            assertElasticsearchEventuallyHas(
              index = Index(s"${indexPrefix}_bibs"),
              id = bibId.withoutCheckDigit,
              json = s"""
                      |{
                      |  "id" : "$bibId",
                      |  "idWithCheckDigit": "${bibId.withCheckDigit}",
                      |  "updatedDate" : "2001-01-01T01:01:01Z",
                      |  "deleted" : false,
                      |  "itemIds": [],
                      |  "holdingsIds": [],
                      |  "orderIds": []
                      |}
                      |""".stripMargin
            )

            assertElasticsearchEventuallyHas(
              index = Index(s"${indexPrefix}_varfields"),
              id = s"bibs-${bibId.withoutCheckDigit}-0",
              json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "bibs",
                      |    "id": "${bibId.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "b",
                      |    "content" : "11111111"
                      |  }
                      |}
                      |""".stripMargin
            )

            assertElasticsearchEventuallyHas(
              index = Index(s"${indexPrefix}_fixedfields"),
              id = s"bibs-${bibId.withoutCheckDigit}-86",
              json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "bibs",
                      |    "id": "${bibId.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                      |  },
                      |  "code": "86",
                      |  "fixedField": {
                      |    "label" : "AGENCY",
                      |    "value" : "1"
                      |  }
                      |}
                      |""".stripMargin
            )

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }

            // Now re-send the same notification, and check the queue clears
            sendNotificationToSQS(
              queue,
              SierraSourcePayload(
                id = bibId.withoutCheckDigit,
                location = location1,
                version = 1)
            )

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }

            // Now send the new notification, and check the record gets updated
            sendNotificationToSQS(
              queue,
              SierraSourcePayload(
                id = bibId.withoutCheckDigit,
                location = location2,
                version = 2)
            )

            assertElasticsearchEventuallyHas(
              index = Index(s"${indexPrefix}_bibs"),
              id = bibId.withoutCheckDigit,
              json = s"""
                      |{
                      |  "id" : "$bibId",
                      |  "idWithCheckDigit": "${bibId.withCheckDigit}",
                      |  "updatedDate" : "2002-02-02T02:02:02Z",
                      |  "deleted" : false,
                      |  "itemIds": [],
                      |  "holdingsIds": [],
                      |  "orderIds": []
                      |}
                      |""".stripMargin
            )

            assertElasticsearchEventuallyHas(
              index = Index(s"${indexPrefix}_varfields"),
              id = s"bibs-${bibId.withoutCheckDigit}-0",
              json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "bibs",
                      |    "id": "${bibId.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "b",
                      |    "content" : "22222222"
                      |  }
                      |}
                      |""".stripMargin
            )

            assertElasticsearchEventuallyHas(
              index = Index(s"${indexPrefix}_fixedfields"),
              id = s"bibs-${bibId.withoutCheckDigit}-86",
              json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "bibs",
                      |    "id": "${bibId.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${bibId.withCheckDigit}"
                      |  },
                      |  "code": "86",
                      |  "fixedField": {
                      |    "label" : "AGENCY",
                      |    "value" : "2"
                      |  }
                      |}
                      |""".stripMargin
            )
          }
      }
    }
  }

  it("indexes item records and their varFields/fixedFields") {
    val location = createS3ObjectLocation

    val itemId1 = createSierraItemNumber
    val itemId2 = createSierraItemNumber

    val transformable = createSierraTransformableWith(
      itemRecords = List(
        SierraItemRecord(
          id = itemId1,
          data = s"""
            |{
            |  "id" : "$itemId1",
            |  "updatedDate" : "2001-01-01T01:01:01Z",
            |  "deleted" : false,
            |  "varFields" : [
            |    {
            |      "fieldTag" : "b",
            |      "content" : "22501328220"
            |    }
            |  ],
            |  "fixedFields": {
            |    "86": {
            |      "label" : "AGENCY",
            |       "value" : "1"
            |    }
            |  }
            |}
            |""".stripMargin,
          modifiedDate = Instant.now(),
          bibIds = List()
        ),
        SierraItemRecord(
          id = itemId2,
          data = s"""
                    |{
                    |  "id" : "$itemId2",
                    |  "idWithCheckDigit": "${itemId2.withCheckDigit}",
                    |  "updatedDate" : "2002-02-02T02:02:02Z",
                    |  "deleted" : true,
                    |  "varFields" : [
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
                    |  ],
                    |  "fixedFields": {
                    |    "265": {
                    |      "label" : "Inherit Location",
                    |      "value" : false
                    |    }
                    |  }
                    |}
                    |""".stripMargin,
          modifiedDate = Instant.now(),
          bibIds = List()
        )
      )
    )
    val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
      initialEntries = Map(location -> transformable)
    )

    withIndices { indexPrefix =>
      withLocalSqsQueue() { queue =>
        withWorker(queue, store, indexPrefix) { _ =>
          sendNotificationToSQS(
            queue,
            SierraSourcePayload(
              id = transformable.sierraId.withoutCheckDigit,
              location = location,
              version = 1
            )
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_items"),
            id = itemId1.withoutCheckDigit,
            json = s"""
                      |{
                      |  "id" : "$itemId1",
                      |  "idWithCheckDigit": "${itemId1.withCheckDigit}",
                      |  "updatedDate" : "2001-01-01T01:01:01Z",
                      |  "deleted" : false
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_items"),
            id = itemId2.withoutCheckDigit,
            json = s"""
                  |{
                  |  "id" : "$itemId2",
                  |  "idWithCheckDigit": "${itemId2.withCheckDigit}",
                  |  "updatedDate" : "2002-02-02T02:02:02Z",
                  |  "deleted" : true
                  |}
                  |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"items-${itemId1.withoutCheckDigit}-0",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "items",
                      |    "id": "${itemId1.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${itemId1.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "b",
                      |    "content" : "22501328220"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"items-${itemId2.withoutCheckDigit}-0",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "items",
                      |    "id": "${itemId2.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${itemId2.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "c",
                      |    "marcTag" : "949",
                      |    "ind1" : " ",
                      |    "ind2" : " ",
                      |    "subfields" : [
                      |      {
                      |        "tag" : "a",
                      |        "content" : "/RHO"
                      |      }
                      |    ]
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"items-${itemId1.withoutCheckDigit}-86",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "items",
                      |    "id": "${itemId1.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${itemId1.withCheckDigit}"
                      |  },
                      |  "code": "86",
                      |  "fixedField": {
                      |    "label" : "AGENCY",
                      |    "value" : "1"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"items-${itemId2.withoutCheckDigit}-265",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "items",
                      |    "id": "${itemId2.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${itemId2.withCheckDigit}"
                      |  },
                      |  "code": "265",
                      |  "fixedField": {
                      |    "label" : "Inherit Location",
                      |    "value" : false
                      |  }
                      |}
                      |""".stripMargin
          )
        }
      }
    }
  }

  it("indexes holdings records and their varFields/fixedFields") {
    val location = createS3ObjectLocation

    val holdingsId1 = createSierraHoldingsNumber
    val holdingsId2 = createSierraHoldingsNumber

    val transformable = createSierraTransformableWith(
      holdingsRecords = List(
        SierraHoldingsRecord(
          id = holdingsId1,
          data = s"""
                    |{
                    |  "id" : "$holdingsId1",
                    |  "updatedDate" : "2001-01-01T01:01:01Z",
                    |  "deleted" : false,
                    |  "varFields" : [
                    |    {
                    |      "fieldTag" : "b",
                    |      "content" : "22501328220"
                    |    }
                    |  ],
                    |  "fixedFields": {
                    |    "86": {
                    |      "label" : "AGENCY",
                    |       "value" : "1"
                    |    }
                    |  }
                    |}
                    |""".stripMargin,
          modifiedDate = Instant.now(),
          bibIds = List()
        ),
        SierraHoldingsRecord(
          id = holdingsId2,
          data = s"""
                    |{
                    |  "id" : "$holdingsId2",
                    |  "updatedDate" : "2002-02-02T02:02:02Z",
                    |  "deleted" : true,
                    |  "varFields" : [
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
                    |  ],
                    |  "fixedFields": {
                    |    "265": {
                    |      "label" : "Inherit Location",
                    |      "value" : false
                    |    }
                    |  }
                    |}
                    |""".stripMargin,
          modifiedDate = Instant.now(),
          bibIds = List()
        )
      )
    )
    val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
      initialEntries = Map(location -> transformable)
    )

    withIndices { indexPrefix =>
      withLocalSqsQueue() { queue =>
        withWorker(queue, store, indexPrefix) { _ =>
          sendNotificationToSQS(
            queue,
            SierraSourcePayload(
              id = transformable.sierraId.withoutCheckDigit,
              location = location,
              version = 1
            )
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_holdings"),
            id = holdingsId1.withoutCheckDigit,
            json = s"""
                      |{
                      |  "id" : "$holdingsId1",
                      |  "idWithCheckDigit": "${holdingsId1.withCheckDigit}",
                      |  "updatedDate" : "2001-01-01T01:01:01Z",
                      |  "deleted" : false
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_holdings"),
            id = holdingsId2.withoutCheckDigit,
            json = s"""
                      |{
                      |  "id" : "$holdingsId2",
                      |  "idWithCheckDigit": "${holdingsId2.withCheckDigit}",
                      |  "updatedDate" : "2002-02-02T02:02:02Z",
                      |  "deleted" : true
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"holdings-${holdingsId1.withoutCheckDigit}-0",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "holdings",
                      |    "id": "${holdingsId1.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${holdingsId1.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "b",
                      |    "content" : "22501328220"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"holdings-${holdingsId2.withoutCheckDigit}-0",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "holdings",
                      |    "id": "${holdingsId2.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${holdingsId2.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "c",
                      |    "marcTag" : "949",
                      |    "ind1" : " ",
                      |    "ind2" : " ",
                      |    "subfields" : [
                      |      {
                      |        "tag" : "a",
                      |        "content" : "/RHO"
                      |      }
                      |    ]
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"holdings-${holdingsId1.withoutCheckDigit}-86",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "holdings",
                      |    "id": "${holdingsId1.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${holdingsId1.withCheckDigit}"
                      |  },
                      |  "code": "86",
                      |  "fixedField": {
                      |    "label" : "AGENCY",
                      |    "value" : "1"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"holdings-${holdingsId2.withoutCheckDigit}-265",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "holdings",
                      |    "id": "${holdingsId2.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${holdingsId2.withCheckDigit}"
                      |  },
                      |  "code": "265",
                      |  "fixedField": {
                      |    "label" : "Inherit Location",
                      |    "value" : false
                      |  }
                      |}
                      |""".stripMargin
          )
        }
      }
    }
  }

  it("indexes order records and their varFields/fixedFields") {
    val location = createS3ObjectLocation

    val orderId1 = createSierraOrderNumber
    val orderId2 = createSierraOrderNumber

    val transformable = createSierraTransformableWith(
      orderRecords = List(
        SierraOrderRecord(
          id = orderId1,
          data = s"""
                    |{
                    |  "id" : "$orderId1",
                    |  "updatedDate" : "2001-01-01T01:01:01Z",
                    |  "deleted" : false,
                    |  "varFields" : [
                    |    {
                    |      "fieldTag" : "b",
                    |      "content" : "22501328220"
                    |    }
                    |  ],
                    |  "fixedFields": {
                    |    "86": {
                    |      "label" : "AGENCY",
                    |       "value" : "1"
                    |    }
                    |  }
                    |}
                    |""".stripMargin,
          modifiedDate = Instant.now(),
          bibIds = List()
        ),
        SierraOrderRecord(
          id = orderId2,
          data = s"""
                    |{
                    |  "id" : "$orderId2",
                    |  "updatedDate" : "2002-02-02T02:02:02Z",
                    |  "deleted" : true,
                    |  "varFields" : [
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
                    |  ],
                    |  "fixedFields": {
                    |    "265": {
                    |      "label" : "Inherit Location",
                    |      "value" : false
                    |    }
                    |  }
                    |}
                    |""".stripMargin,
          modifiedDate = Instant.now(),
          bibIds = List()
        )
      )
    )
    val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
      initialEntries = Map(location -> transformable)
    )

    withIndices { indexPrefix =>
      withLocalSqsQueue() { queue =>
        withWorker(queue, store, indexPrefix) { _ =>
          sendNotificationToSQS(
            queue,
            SierraSourcePayload(
              id = transformable.sierraId.withoutCheckDigit,
              location = location,
              version = 1
            )
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_orders"),
            id = orderId1.withoutCheckDigit,
            json = s"""
                      |{
                      |  "id" : "$orderId1",
                      |  "idWithCheckDigit": "${orderId1.withCheckDigit}",
                      |  "updatedDate" : "2001-01-01T01:01:01Z",
                      |  "deleted" : false
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_orders"),
            id = orderId2.withoutCheckDigit,
            json = s"""
                      |{
                      |  "id" : "$orderId2",
                      |  "idWithCheckDigit": "${orderId2.withCheckDigit}",
                      |  "updatedDate" : "2002-02-02T02:02:02Z",
                      |  "deleted" : true
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"orders-${orderId1.withoutCheckDigit}-0",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "orders",
                      |    "id": "${orderId1.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${orderId1.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "b",
                      |    "content" : "22501328220"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_varfields"),
            id = s"orders-${orderId2.withoutCheckDigit}-0",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "orders",
                      |    "id": "${orderId2.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${orderId2.withCheckDigit}"
                      |  },
                      |  "position": 0,
                      |  "varField": {
                      |    "fieldTag" : "c",
                      |    "marcTag" : "949",
                      |    "ind1" : " ",
                      |    "ind2" : " ",
                      |    "subfields" : [
                      |      {
                      |        "tag" : "a",
                      |        "content" : "/RHO"
                      |      }
                      |    ]
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"orders-${orderId1.withoutCheckDigit}-86",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "orders",
                      |    "id": "${orderId1.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${orderId1.withCheckDigit}"
                      |  },
                      |  "code": "86",
                      |  "fixedField": {
                      |    "label" : "AGENCY",
                      |    "value" : "1"
                      |  }
                      |}
                      |""".stripMargin
          )

          assertElasticsearchEventuallyHas(
            index = Index(s"${indexPrefix}_fixedfields"),
            id = s"orders-${orderId2.withoutCheckDigit}-265",
            json = s"""
                      |{
                      |  "parent": {
                      |    "recordType": "orders",
                      |    "id": "${orderId2.withoutCheckDigit}",
                      |    "idWithCheckDigit": "${orderId2.withCheckDigit}"
                      |  },
                      |  "code": "265",
                      |  "fixedField": {
                      |    "label" : "Inherit Location",
                      |    "value" : false
                      |  }
                      |}
                      |""".stripMargin
          )
        }
      }
    }
  }

  it("DLQs a message if one of the bulk requests fails") {
    withIndices { indexPrefix =>
      val location = createS3ObjectLocation

      val bibId = createSierraBibNumber

      val transformable = createSierraTransformableWith(
        maybeBibRecord = Some(
          createSierraBibRecordWith(
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
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)
            }
          }
      }
    }
  }
}
