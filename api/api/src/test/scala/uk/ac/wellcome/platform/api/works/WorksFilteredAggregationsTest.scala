package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.models.work.internal.Format._
import uk.ac.wellcome.models.Implicits._

class WorksFilteredAggregationsTest extends ApiWorksTestBase {

  it(
    "filters an aggregation with a filter that is not paired to the aggregation") {
    withWorksApi {
      case (worksIndex, routes) =>
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
        ).map {
          case (format, language) =>
            identifiedWork()
              .format(format)
              .language(language)
        }

        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?workType=a&aggregations=language") {
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
                            .sortBy { _.state.canonicalId }
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it(
    "filters an aggregation with a filter that is paired to another aggregation") {
    withWorksApi {
      case (worksIndex, routes) =>
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
        ).map {
          case (format, language) =>
            identifiedWork()
              .format(format)
              .language(language)
        }

        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?workType=a&aggregations=language,workType") {
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
                },
                "workType" : {
                  "type": "Aggregation",
                  "buckets" : [
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
                            .sortBy { _.state.canonicalId }
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("filters results but not aggregations paired with an applied filter") {
    withWorksApi {
      case (worksIndex, routes) =>
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
        ).map {
          case (format, language) =>
            identifiedWork()
              .format(format)
              .language(language)
        }

        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?workType=a&aggregations=workType") {
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
                            .sortBy { _.state.canonicalId }
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }
}
