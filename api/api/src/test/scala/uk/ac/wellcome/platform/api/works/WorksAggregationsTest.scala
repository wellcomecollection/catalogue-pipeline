package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal.Format.{Books, Journals, Pictures}
import uk.ac.wellcome.models.work.internal._

class WorksAggregationsTest extends ApiWorksTestBase {

  it("supports fetching the format aggregation") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val formats = List(
          Books,
          Books,
          Books,
          Pictures,
          Pictures,
          Journals
        )

        val works = formats.map { identifiedWork().format(_) }

        insertIntoElasticsearch(worksIndex, works: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=workType") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = works.size)},
              "aggregations": {
                "type" : "Aggregations",
                "workType": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "id" : "a",
                        "label" : "Books",
                        "type" : "Format"
                      },
                      "count" : 3,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "id" : "k",
                        "label" : "Pictures",
                        "type" : "Format"
                      },
                      "count" : 2,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "id" : "d",
                        "label" : "Journals",
                        "type" : "Format"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [
                ${works.sortBy { _.state.canonicalId }.map(workResponse).mkString(",")}
              ]
            }
          """
        }
    }
  }

  it("supports fetching the genre aggregation") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val concept0 = Concept("conceptLabel")
        val concept1 = Place("placeLabel")
        val concept2 = Period(
          id = IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifierWith(
              ontologyType = "Period"
            )
          ),
          label = "periodLabel",
          range = None
        )

        val genre = Genre(
          label = "Electronic books.",
          concepts = List(concept0, concept1, concept2)
        )

        val work = identifiedWork().genres(List(genre))

        insertIntoElasticsearch(worksIndex, work)

        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=genres") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 1)},
              "aggregations": {
                "type" : "Aggregations",
                "genres": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "label" : "conceptLabel",
                        "concepts": [],
                        "type" : "Genre"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    },
                           {
                      "data" : {
                        "label" : "periodLabel",
                        "concepts": [],
                        "type" : "Genre"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    },
                           {
                      "data" : {
                        "label" : "placeLabel",
                        "concepts": [],
                        "type" : "Genre"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${workResponse(work)}]
            }
          """
        }
    }
  }

  it("supports aggregating on dates by from year") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val dates = List("1st May 1970", "1970", "1976", "1970-1979")

        val works = dates
          .map { dateLabel =>
            identifiedWork()
              .production(
                List(createProductionEventWith(dateLabel = Some(dateLabel))))
          }
          .sortBy { _.state.canonicalId }

        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?aggregations=production.dates") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = works.size)},
              "aggregations": {
                "type" : "Aggregations",
                "production.dates": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "label": "1970",
                        "type": "Period"
                      },
                      "count" : 3,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "label": "1976",
                        "type": "Period"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works.map(workResponse).mkString(",")}]
            }
          """
        }
    }
  }

  it("supports aggregating on language") {
    val languages = List(
      Language("English", Some("eng")),
      Language("German", Some("ger")),
      Language("German", Some("ger"))
    )

    val works = languages.map { identifiedWork().language(_) }

    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=language") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = works.size)},
              "aggregations": {
                "type" : "Aggregations",
                "language": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "id": "ger",
                        "label": "German",
                        "type": "Language"
                      },
                      "count" : 2,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "id": "eng",
                        "label": "English",
                        "type": "Language"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works.sortBy { _.state.canonicalId }.map(workResponse).mkString(",")}]
            }
          """
        }
    }
  }

  it("supports aggregating on subject, ordered by frequency") {
    val paeleoNeuroBiology = createSubjectWith(label = "paeleoNeuroBiology")
    val realAnalysis = createSubjectWith(label = "realAnalysis")

    val subjectLists = List(
      List(paeleoNeuroBiology),
      List(realAnalysis),
      List(realAnalysis),
      List(paeleoNeuroBiology, realAnalysis),
      List.empty
    )

    val works = subjectLists
      .map { identifiedWork().subjects(_) }

    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=subjects") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = works.size)},
              "aggregations": {
                "type" : "Aggregations",
                "subjects": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "label": "realAnalysis",
                        "concepts": [],
                        "type": "Subject"
                      },
                      "count" : 3,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "label": "paeleoNeuroBiology",
                        "concepts": [],
                        "type": "Subject"
                      },
                      "count" : 2,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works.sortBy { _.state.canonicalId }.map(workResponse).mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("supports aggregating on license") {
    def createLicensedWork(licenses: Seq[License]): Work.Visible[WorkState.Identified] = {
      val items =
        licenses.map { license =>
          createDigitalItemWith(license = Some(license))
        }.toList

      identifiedWork().items(items)
    }

    val licenseLists = List(
      List(License.CCBY),
      List(License.CCBY),
      List(License.CCBYNC),
      List(License.CCBY, License.CCBYNC),
      List.empty
    )

    val works = licenseLists.map { createLicensedWork(_) }

    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=license") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = works.size)},
              "aggregations": {
                "type" : "Aggregations",
                "license": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "count" : 3,
                      "data" : {
                        "id" : "cc-by",
                        "label" : "Attribution 4.0 International (CC BY 4.0)",
                        "type" : "License",
                        "url" : "http://creativecommons.org/licenses/by/4.0/"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : {
                        "id" : "cc-by-nc",
                        "label" : "Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)",
                        "type" : "License",
                        "url" : "https://creativecommons.org/licenses/by-nc/4.0/"
                      },
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works.sortBy { _.state.canonicalId }.map(workResponse).mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("supports aggregating on locationType") {
    val locations = List(
      createPhysicalLocation,
      createPhysicalLocation,
      createDigitalLocation,
      createDigitalLocation,
      createDigitalLocation
    )

    val works = locations.map { loc =>
      identifiedWork()
        .items(List(createIdentifiedItemWith(locations = List(loc))))
    }

    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?aggregations=locationType") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = works.size)},
              "aggregations" : {
                "locationType" : {
                  "buckets" : [
                    {
                      "count" : 3,
                      "data" : {
                        "label" : "Online",
                        "type" : "DigitalLocation"
                      },
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : {
                        "label" : "In the library",
                        "type" : "PhysicalLocation"
                      },
                      "type" : "AggregationBucket"
                    }
                  ],
                  "type" : "Aggregation"
                },
                "type" : "Aggregations"
              },
              "results": [${works.sortBy { _.state.canonicalId }.map(workResponse).mkString(",")}]
            }
          """.stripMargin
        }
    }
  }
}
