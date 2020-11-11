package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal.{Language, Work, WorkState}
import uk.ac.wellcome.models.work.internal.Format._
import uk.ac.wellcome.models.Implicits._

class WorksFilteredAggregationsTest extends ApiWorksTestBase {
  
  val bashkir = Language(label = "Bashkir", id = "bak")
  val marathi = Language(label = "Marathi", id = "mar")
  val quechua = Language(label = "Quechua", id = "que")
  val chechen = Language(label = "Chechen", id = "che")

  val works: List[Work.Visible[WorkState.Identified]] = List(
    (Books, bashkir),
    (Journals, marathi),
    (Pictures, quechua),
    (Audio, bashkir),
    (Books, bashkir),
    (Books, bashkir),
    (Journals, quechua),
    (Books, marathi),
    (Journals, quechua),
    (Audio, chechen)
  ).map {
    case (format, language) =>
      identifiedWork()
        .format(format)
        .language(language)
  }

  it(
    "filters an aggregation with a filter that is not paired to the aggregation") {
    withWorksApi {
      case (worksIndex, routes) =>
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
                      "data" : ${language(bashkir)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : ${language(marathi)},
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
<<<<<<< HEAD
                      "data" : ${language(bashkir)},
=======
                      "data" : {
                        "id" : "ba",
                        "label" : "Bashkir",
                        "type" : "Language"
                      },
>>>>>>> Get aggregations and filters working for language
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
<<<<<<< HEAD
                      "data" : ${language(marathi)},
=======
                      "data" : {
                        "id" : "mr",
                        "label" : "Marathi",
                        "type" : "Language"
                      },
>>>>>>> Get aggregations and filters working for language
                      "type" : "AggregationBucket"
                    }
                  ]
                },
                "workType" : {
                  "type": "Aggregation",
                  "buckets" : [
                    {
                      "count" : 4,
                      "data" : ${format(Books)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 3,
                      "data" : ${format(Journals)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : ${format(Audio)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : ${format(Pictures)},
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
                      "data" : ${format(Books)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 3,
                      "data" : ${format(Journals)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : ${format(Audio)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : ${format(Pictures)},
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
