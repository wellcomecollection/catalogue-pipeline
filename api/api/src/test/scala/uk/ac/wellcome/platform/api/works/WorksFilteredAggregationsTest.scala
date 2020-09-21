package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.models.work.internal.Format._

class WorksFilteredAggregationsTest extends ApiWorksTestBase {

  it("filters aggregations with filters that are not paired to the aggregation") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val works = List(
          (Books, Language("Bark", Some("dogs"))),
          (Journals, Language("Meow", Some("cats"))),
          (Pictures, Language("Quack", Some("ducks"))),
          (Audio, Language("Bark", Some("dogs"))),
          (Books, Language("Bark", Some("dogs"))),
          (Books, Language("Bark", Some("dogs"))),
          (Journals, Language("Quack", Some("ducks"))),
          (Books, Language("Meow", Some("cats"))),
          (Journals, Language("Quack", Some("ducks"))),
          (Audio, Language("Croak", Some("frogs")))
        ).zipWithIndex.map {
          case ((format, language), i) =>
            createIdentifiedWorkWith(
              title = Some("Robust rambutan"),
              canonicalId = i.toString,
              format = Some(format),
              language = Some(language)
            )
        }

        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?query=rambutan&workType=a&aggregations=language") {
          Status.OK -> s"""
            {
              ${resultList(
                            apiPrefix,
                            totalResults =
                              works.count(_.data.format.get == Books))},
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
                            .filter(_.data.format.get == Books)
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("filters results but not aggregations paired with an applied filter") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val works = List(
          (Books, Language("Bark", Some("dogs"))),
          (Journals, Language("Meow", Some("cats"))),
          (Pictures, Language("Quack", Some("ducks"))),
          (Audio, Language("Bark", Some("dogs"))),
          (Books, Language("Bark", Some("dogs"))),
          (Books, Language("Bark", Some("dogs"))),
          (Journals, Language("Quack", Some("ducks"))),
          (Books, Language("Meow", Some("cats"))),
          (Journals, Language("Quack", Some("ducks"))),
          (Audio, Language("Croak", Some("frogs")))
        ).zipWithIndex.map {
          case ((format, language), i) =>
            createIdentifiedWorkWith(
              title = Some("Robust rambutan"),
              canonicalId = i.toString,
              format = Some(format),
              language = Some(language)
            )
        }

        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?query=rambutan&workType=a&aggregations=workType") {
          Status.OK -> s"""
            {
              ${resultList(
                            apiPrefix,
                            totalResults =
                              works.count(_.data.format.get == Books))},
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
                        "type" : "Format"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 3,
                      "data" : {
                        "id" : "d",
                        "label" : "Journals",
                        "type" : "Format"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : {
                        "id" : "i",
                        "label" : "Audio",
                        "type" : "Format"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : {
                        "id" : "k",
                        "label" : "Pictures",
                        "type" : "Format"
                      },
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works
                            .filter(_.data.format.get == Books)
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }
}
