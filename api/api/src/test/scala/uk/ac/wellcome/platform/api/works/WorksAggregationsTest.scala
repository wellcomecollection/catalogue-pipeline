package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal.Format.{Books, Journals, Pictures}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.{
  ItemsGenerators,
  ProductionEventGenerators
}

class WorksAggregationsTest
    extends ApiWorksTestBase
    with ItemsGenerators
    with ProductionEventGenerators {

  it("supports fetching the format aggregation") {
    withWorksApi {
      case (worksIndex, routes) =>
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
                ${works
            .sortBy { _.state.canonicalId }
            .map(workResponse)
            .mkString(",")}
              ]
            }
          """
        }
    }
  }

  it("supports fetching the genre aggregation") {
    withWorksApi {
      case (worksIndex, routes) =>
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
    withWorksApi {
      case (worksIndex, routes) =>
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
    val english = Language(label = "English", id = "eng")
    val german = Language(label = "German", id = "ger")

    val languages = List(english, german, german)

    val works = languages.map { identifiedWork().language(_) }

    withWorksApi {
      case (worksIndex, routes) =>
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
                      "data" : ${language(german)},
                      "count" : 2,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : ${language(english)},
                      "count" : 1,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works
            .sortBy { _.state.canonicalId }
            .map(workResponse)
            .mkString(",")}]
            }
          """
        }
    }
  }

  it("supports aggregating on subject, ordered by frequency") {
    val paleoNeuroBiology = createSubjectWith(label = "paleoNeuroBiology")
    val realAnalysis = createSubjectWith(label = "realAnalysis")

    val subjectLists = List(
      List(paleoNeuroBiology),
      List(realAnalysis),
      List(realAnalysis),
      List(paleoNeuroBiology, realAnalysis),
      List.empty
    )

    val works = subjectLists
      .map { identifiedWork().subjects(_) }

    withWorksApi {
      case (worksIndex, routes) =>
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
                      "data" : ${subject(realAnalysis, showConcepts = false)},
                      "count" : 3,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : ${subject(
                            paleoNeuroBiology,
                            showConcepts = false)},
                      "count" : 2,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works
                            .sortBy { _.state.canonicalId }
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("supports aggregating on license") {
    def createLicensedWork(
      licenses: Seq[License]): Work.Visible[WorkState.Identified] = {
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

    withWorksApi {
      case (worksIndex, routes) =>
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
                      "data" : ${license(License.CCBY)},
                      "type" : "AggregationBucket"
                    },
                    {
                      "count" : 2,
                      "data" : ${license(License.CCBYNC)},
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [${works
                            .sortBy { _.state.canonicalId }
                            .map(workResponse)
                            .mkString(",")}]
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

    withWorksApi {
      case (worksIndex, routes) =>
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
              "results": [${works
                            .sortBy { _.state.canonicalId }
                            .map(workResponse)
                            .mkString(",")}]
            }
          """.stripMargin
        }
    }
  }
}
