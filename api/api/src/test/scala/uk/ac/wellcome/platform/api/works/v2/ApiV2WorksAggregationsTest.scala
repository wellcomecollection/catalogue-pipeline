package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal._

class ApiV2WorksAggregationsTest extends ApiV2WorksTestBase {

  it("supports fetching the workType aggregation") {
    withApi {
      case (indexV2, routes) =>
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

        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=workType") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 5)},
              "results": [],
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
                       "id" : "d",
                       "label" : "Journals",
                       "type" : "WorkType"
                     },
                     "count" : 1,
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
                   }
                 ]
               }
              }
            }
          """
        }
    }
  }

  it("supports fetching the genre aggregation") {
    withApi {
      case (indexV2, routes) =>
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

        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=genres") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 1)},
              "results": [],
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
              }
            }
          """
        }
    }
  }

  it("supports aggregating on dates by from year") {
    withApi {
      case (indexV2, routes) =>
        val works = List("1st May 1970", "1970", "1976", "1970-1979")
          .map(label => createDatedWork(dateLabel = label))
        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?aggregations=production.dates") {
          Status.OK -> s"""
            {
             ${resultList(apiPrefix, totalResults = 4)},
             "results": [],
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
             }
            }
          """
        }
    }
  }

  it("supports aggregating on language") {

    val works = List(
      createIdentifiedWorkWith(
        language = Some(Language("eng", "English"))
      ),
      createIdentifiedWorkWith(
        language = Some(Language("ger", "German"))
      ),
      createIdentifiedWorkWith(
        language = Some(Language("ger", "German"))
      ),
      createIdentifiedWorkWith(language = None)
    )
    withApi {
      case (indexV2, routes) =>
        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=language") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 4)},
              "results": [],
              "aggregations": {
                "type" : "Aggregations",
                "language": {
                  "type" : "Aggregation",
                  "buckets": [
                    {
                      "data" : {
                        "label": "English",
                        "id": "eng",
                        "type": "Language"
                      },
                      "count" : 1,
                      "type" : "AggregationBucket"
                    },
                    {
                      "data" : {
                        "label": "German",
                        "id": "ger",
                        "type": "Language"
                      },
                      "count" : 2,
                      "type" : "AggregationBucket"
                    }
                  ]
                }
              }
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
    )
    withApi {
      case (indexV2, routes) =>
        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?aggregations=subjects") {
          Status.OK -> s"""
            {
             ${resultList(apiPrefix, totalResults = 5)},
             "results": [],
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
             }
            }
          """.stripMargin
        }
    }
  }
}
