package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal.WorkType.{Books, Journals, Pictures}
import uk.ac.wellcome.models.work.internal._

class WorksAggregationsTest extends ApiWorksTestBase {

  it("supports fetching the workType aggregation") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val work1 = createIdentifiedWorkWith(
          canonicalId = "1",
          title = Some("Working with wombats"),
          workType = Some(Books)
        )
        val work2 = createIdentifiedWorkWith(
          canonicalId = "2",
          title = Some("Working with wombats"),
          workType = Some(Books)
        )
        val work3 = createIdentifiedWorkWith(
          canonicalId = "3",
          title = Some("Working with wombats"),
          workType = Some(Pictures)
        )
        val work4 = createIdentifiedWorkWith(
          canonicalId = "4",
          title = Some("Working with wombats"),
          workType = Some(Pictures)
        )
        val work5 = createIdentifiedWorkWith(
          canonicalId = "5",
          title = Some("Working with wombats"),
          workType = Some(Journals)
        )
        insertIntoElasticsearch(worksIndex, work1, work2, work3, work4, work5)

        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=workType") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 5)},
              "aggregations": {
                "type" : "Aggregations",
                "workType": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "id" : "a",
                        "label" : "Books",
                        "type" : "WorkType"
                      },
                      "count" : 2,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "id" : "k",
                        "label" : "Pictures",
                        "type" : "WorkType"
                      },
                      "count" : 2,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "id" : "d",
                        "label" : "Journals",
                        "type" : "WorkType"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              },
              "results": [
                ${workResponse(work1)},
                ${workResponse(work2)},
                ${workResponse(work3)},
                ${workResponse(work4)},
                ${workResponse(work5)}
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

        val work1 = createIdentifiedWorkWith(
          canonicalId = "1",
          title = Some("Working with wombats"),
          genres = List(genre)
        )

        insertIntoElasticsearch(worksIndex, work1)

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
              "results": [${workResponse(work1)}]
            }
          """
        }
    }
  }

  it("supports aggregating on dates by from year") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val works = List("1st May 1970", "1970", "1976", "1970-1979")
          .map(label => createDatedWork(dateLabel = label))
          .sortBy(_.state.canonicalId)
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?aggregations=production.dates") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 4)},
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
    val works = List(
      createIdentifiedWorkWith(
        language = Some(Language("English", Some("eng")))
      ),
      createIdentifiedWorkWith(
        language = Some(Language("German", Some("ger")))
      ),
      createIdentifiedWorkWith(
        language = Some(Language("German", Some("ger")))
      ),
      createIdentifiedWorkWith(language = None)
    ).sortBy(_.canonicalId)
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=language") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 4)},
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
              "results": [${works.map(workResponse).mkString(",")}]
            }
          """
        }
    }
  }

  it("supports aggregating on subject, ordered by frequency") {

    val paeleoNeuroBiology = createSubjectWith(label = "paeleoNeuroBiology")
    val realAnalysis = createSubjectWith(label = "realAnalysis")

    val works = List(
      createIdentifiedWorkWith(
        subjects = List(paeleoNeuroBiology)
      ),
      createIdentifiedWorkWith(
        subjects = List(realAnalysis)
      ),
      createIdentifiedWorkWith(
        subjects = List(realAnalysis)
      ),
      createIdentifiedWorkWith(
        subjects = List(paeleoNeuroBiology, realAnalysis)
      ),
      createIdentifiedWorkWith(subjects = Nil)
    ).sortBy(_.canonicalId)
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=subjects") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 5)},
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
              "results": [${works.map(workResponse).mkString(",")}]
            }
          """.stripMargin
        }
    }
  }

  it("supports aggregating on license") {

    val works = List(
      createLicensedWork("A", List(License.CCBY)),
      createLicensedWork("B", List(License.CCBYNC)),
      createLicensedWork("C", List(License.CCBY, License.CCBYNC)),
      createLicensedWork("D", Nil)
    )
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=license") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 4)},
              "aggregations": {
                "type" : "Aggregations",
                "license": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "count" : 2,
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
              "results": [${works.map(workResponse).mkString(",")}]
            }
          """.stripMargin
        }
    }
  }
}
