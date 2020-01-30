package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.models.work.internal.WorkType._

class ApiV2FilteredAggregationsTest extends ApiV2WorksTestBase {

  it("filters aggregations with filters that are not paired to the aggregation") {
    withApi {
      case (indexV2, routes) =>
        val works = List(
          (Books, Language("dogs", "Bark")),
          (Journals, Language("cats", "Meow")),
          (Pictures, Language("ducks", "Quack")),
          (Audio, Language("dogs", "Bark")),
          (Books, Language("dogs", "Bark")),
          (Books, Language("dogs", "Bark")),
          (Journals, Language("ducks", "Quack")),
          (Books, Language("cats", "Meow")),
          (Journals, Language("ducks", "Quack")),
          (Audio, Language("frogs", "Croak"))
        ).zipWithIndex.map {
          case ((workType, language), i) =>
            createIdentifiedWorkWith(
              title = Some("Robust rambutan"),
              canonicalId = i.toString,
              workType = Some(workType),
              language = Some(language)
            )
        }

        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?query=rambutan&workType=a&aggregations=language") {
          Status.OK -> s"""
            {
              ${resultList(
                            apiPrefix,
                            totalResults =
                              works.count(_.data.workType.get == Books))},
              "aggregations": {
                "type" : "Aggregations",
                "language": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "count" : 3,
                      "data" : {
                        "id" : "dogs",
                        "label" : "Bark",
                        "type" : "Language"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : {
                        "id" : "cats",
                        "label" : "Meow",
                        "type" : "Language"
                      },
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works
                            .filter(_.data.workType.get == Books)
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("filters results but not aggregations paired with an applied filter") {
    withApi {
      case (indexV2, routes) =>
        val works = List(
          (Books, Language("dogs", "Bark")),
          (Journals, Language("cats", "Meow")),
          (Pictures, Language("ducks", "Quack")),
          (Audio, Language("dogs", "Bark")),
          (Books, Language("dogs", "Bark")),
          (Books, Language("dogs", "Bark")),
          (Journals, Language("ducks", "Quack")),
          (Books, Language("cats", "Meow")),
          (Journals, Language("ducks", "Quack")),
          (Audio, Language("frogs", "Croak"))
        ).zipWithIndex.map {
          case ((workType, language), i) =>
            createIdentifiedWorkWith(
              title = Some("Robust rambutan"),
              canonicalId = i.toString,
              workType = Some(workType),
              language = Some(language)
            )
        }

        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?query=rambutan&workType=a&aggregations=workType") {
          Status.OK -> s"""
            {
              ${resultList(
                            apiPrefix,
                            totalResults =
                              works.count(_.data.workType.get == Books))},
              "aggregations": {
                "type" : "Aggregations",
                "workType": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "count" : 4,
                      "data" : {
                        "id" : "a",
                        "label" : "Books",
                        "type" : "WorkType"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 3,
                      "data" : {
                        "id" : "d",
                        "label" : "Journals",
                        "type" : "WorkType"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : {
                        "id" : "i",
                        "label" : "Audio",
                        "type" : "WorkType"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : {
                        "id" : "k",
                        "label" : "Pictures",
                        "type" : "WorkType"
                      },
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works
                            .filter(_.data.workType.get == Books)
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }
}
