package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal.{Language, Work, WorkState}
import uk.ac.wellcome.models.work.internal.Format._
import uk.ac.wellcome.models.Implicits._

class WorksFilteredAggregationsTest extends ApiWorksTestBase {

  val bark = Language(label = "Bark", id = "dogs")
  val meow = Language(label = "Meow", id = "cats")
  val quack = Language(label = "Quack", id = "ducks")
  val croak = Language(label = "Croak", id = "frogs")

  val works: List[Work.Visible[WorkState.Identified]] = List(
    (Books, bark),
    (Journals, meow),
    (Pictures, quack),
    (Audio, bark),
    (Books, bark),
    (Books, bark),
    (Journals, quack),
    (Books, meow),
    (Journals, quack),
    (Audio, croak)
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
                      "data" : ${language(bark)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : ${language(meow)},
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
                      "data" : ${language(bark)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : ${language(meow)},
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
