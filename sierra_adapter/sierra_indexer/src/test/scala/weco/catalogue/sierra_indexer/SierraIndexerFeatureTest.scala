package weco.catalogue.sierra_indexer

import com.sksamuel.elastic4s.Index
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
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
      }.toList,
      holdingsRecords = holdingsIds.map { id =>
        createSierraHoldingsRecordWith(id = id)
      }.toList,
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
                |  "holdingsIds": [$holdingsIdsList]
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
}
