package uk.ac.wellcome.platform.api.works.v2

import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import uk.ac.wellcome.models.work.internal._

class ApiV2WorksAggregationsTest extends ApiV2WorksTestBase {

  it("supports fetching the workType aggregation") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val work1 = createIdentifiedWorkWith(
          canonicalId = "1",
          title = "Working with wombats",
          workType = Some(WorkType("a", "Books"))
        )
        val work2 = createIdentifiedWorkWith(
          canonicalId = "2",
          title = "Working with wombats",
          workType = Some(WorkType("a", "Books"))
        )
        val work3 = createIdentifiedWorkWith(
          canonicalId = "3",
          title = "Working with wombats",
          workType = Some(WorkType("k", "Pictures"))
        )
        val work4 = createIdentifiedWorkWith(
          canonicalId = "4",
          title = "Working with wombats",
          workType = Some(WorkType("k", "Pictures"))
        )
        val work5 = createIdentifiedWorkWith(
          canonicalId = "5",
          title = "Working with wombats",
          workType = Some(WorkType("d", "Journals"))
        )
        insertIntoElasticsearch(indexV2, work1, work2, work3, work4, work5)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?aggregations=workType",
            andExpect = Status.Ok,
            withJsonBody = s"""
              |{
              | ${resultList(apiPrefix, totalResults = 5)},
              | "results": [],
              | "aggregations": {
              | "type" : "Aggregations",
              |  "workType": {
              |    "type" : "Aggregation",
              |    "buckets": [
              |      {
              |        "data" : {
              |          "id" : "a",
              |          "label" : "Books",
              |          "type" : "WorkType"
              |        },
              |        "count" : 2,
              |        "type" : "AggregationBucket"
              |      },
              |      {
              |        "data" : {
              |          "id" : "d",
              |          "label" : "Journals",
              |          "type" : "WorkType"
              |        },
              |        "count" : 1,
              |        "type" : "AggregationBucket"
              |      },
              |      {
              |        "data" : {
              |          "id" : "k",
              |          "label" : "Pictures",
              |          "type" : "WorkType"
              |        },
              |        "count" : 2,
              |        "type" : "AggregationBucket"
              |      }
              |    ]
              |  }
              | }
              |}
            """.stripMargin
          )
        }
    }
  }

  it("supports fetching the genre aggregation") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val concept0 = Unidentifiable(Concept("conceptLabel"))
        val concept1 = Unidentifiable(Place("placeLabel"))
        val concept2 = Identified(
          canonicalId = createCanonicalId,
          sourceIdentifier = createSourceIdentifierWith(
            ontologyType = "Period"
          ),
          agent = Period("periodLabel")
        )

        val genre = Genre(
          label = "Electronic books.",
          concepts = List(concept0, concept1, concept2)
        )

        val work1 = createIdentifiedWorkWith(
          canonicalId = "1",
          title = "Working with wombats",
          genres = List(genre)
        )

        insertIntoElasticsearch(indexV2, work1)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?aggregations=genres",
            andExpect = Status.Ok,
            withJsonBody = s"""
              |{
              | ${resultList(apiPrefix, totalResults = 1)},
              | "results": [],
              | "aggregations": {
              | "type" : "Aggregations",
              |  "genres": {
              |    "type" : "Aggregation",
              |    "buckets": [
              |      {
              |        "data" : {
              |          "label" : "conceptLabel",
              |          "concepts": [],
              |          "type" : "Genre"
              |        },
              |        "count" : 1,
              |        "type" : "AggregationBucket"
              |      },
              |             {
              |        "data" : {
              |          "label" : "periodLabel",
              |          "concepts": [],
              |          "type" : "Genre"
              |        },
              |        "count" : 1,
              |        "type" : "AggregationBucket"
              |      },
              |             {
              |        "data" : {
              |          "label" : "placeLabel",
              |          "concepts": [],
              |          "type" : "Genre"
              |        },
              |        "count" : 1,
              |        "type" : "AggregationBucket"
              |      }
              |    ]
              |  }
              | }
              |}
          """.stripMargin
          )
        }
    }
  }

  it("supports aggregating on dates by from year") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val works = List("1st May 1970", "1970", "1976", "1970-1979")
          .map(label => createDatedWork(dateLabel = label))
        insertIntoElasticsearch(indexV2, works: _*)
        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?aggregations=production.dates",
            andExpect = Status.Ok,
            withJsonBody = s"""
              |{
              | ${resultList(apiPrefix, totalResults = 4)},
              | "results": [],
              | "aggregations": {
              |   "type" : "Aggregations",
              |   "production.dates": {
              |     "type" : "Aggregation",
              |     "buckets": [
              |       {
              |         "data" : {
              |           "label": "1970",
              |           "type": "Period"
              |         },
              |         "count" : 3,
              |         "type" : "AggregationBucket"
              |       },
              |       {
              |         "data" : {
              |           "label": "1976",
              |           "type": "Period"
              |         },
              |         "count" : 1,
              |         "type" : "AggregationBucket"
              |       }
              |     ]
              |   }
              | }
              |}
          """.stripMargin
          )
        }
    }
  }
}
