package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.Implicits._
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.Format._

class WorksFilteredAggregationsTest extends ApiWorksTestBase {

  val bashkir = Language(label = "Bashkir", id = "bak")
  val marathi = Language(label = "Marathi", id = "mar")
  val quechua = Language(label = "Quechua", id = "que")
  val chechen = Language(label = "Chechen", id = "che")

  /*
   * | workType     | count |
   * |--------------|-------|
   * | a / Books    | 4     |
   * | d / Journals | 3     |
   * | i / Audio    | 2     |
   * | k / Pictures | 1     |
   *
   * | language      | count |
   * |---------------|-------|
   * | bak / Bashkir | 4     |
   * | que / Quechua | 3     |
   * | mar / Marathi  | 2     |
   * | che / Chechen | 1     |
   *
   */
  val works: List[Work.Visible[WorkState.Indexed]] = List(
    (Books, bashkir), // a
    (Journals, marathi), // d
    (Pictures, quechua), // k
    (Audio, bashkir), // i
    (Books, bashkir), // a
    (Books, bashkir), // a
    (Journals, quechua), // d
    (Books, marathi), // a
    (Journals, quechua), // d
    (Audio, chechen) // i
  ).map {
    case (format, language) =>
      indexedWork()
        .format(format)
        .languages(List(language))
  }

  describe(
    "filters aggregation buckets with any filters that are not paired to the aggregation") {
    it("when those filters do not have a paired aggregation present") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(
            routes,
            // We expect to see the language buckets for only the works with workType=a
            s"/$apiPrefix/works?workType=a&aggregations=languages") {
            Status.OK -> s"""
            {
              ${resultList(
                              apiPrefix,
                              totalResults =
                                works.count(_.data.format.get == Books))},
              "aggregations": {
                "type" : "Aggregations",
                "languages": {
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

    it("when those filters do have a paired aggregation present") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(
            routes,
            // We expect to see the language buckets for only the works with workType=a
            // We expect to see the workType buckets for all of the works
            s"/$apiPrefix/works?workType=a&aggregations=languages,workType"
          ) {
            Status.OK -> s"""
            {
              ${resultList(
                              apiPrefix,
                              totalResults =
                                works.count(_.data.format.get == Books))},
              "aggregations": {
                "type" : "Aggregations",
                "languages": {
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

    it("but still returns empty buckets if their paired filter is present") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(
            routes,
            // We expect to see the workType buckets for worktype i/Audio, because that
            // has the language che/Chechen, and for a/Books, because a filter for it is
            // present
            s"/$apiPrefix/works?workType=a&languages=che&aggregations=workType"
          ) {
            Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 0, totalPages = 0)},
              "aggregations": {
                "type" : "Aggregations",
                "workType" : {
                  "type": "Aggregation",
                  "buckets" : [
                    {
                      "count" : 0,
                      "data" : ${format(Books)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 0,
                      "data" : ${format(Journals)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 1,
                      "data" : ${format(Audio)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 0,
                      "data" : ${format(Pictures)},
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": []
            }
          """.stripMargin
          }
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
