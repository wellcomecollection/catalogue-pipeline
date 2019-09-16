package uk.ac.wellcome.platform.api.works.v2

import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.models.work.internal._

class ApiV2WorksTest extends ApiV2WorksTestBase {
  it("returns a list of works") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val works = createIdentifiedWorks(count = 3).sortBy { _.canonicalId }

        insertIntoElasticsearch(indexV2, works: _*)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(apiPrefix, totalResults = 3)},
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${works(0).canonicalId}",
               |     "title": "${works(0).title}"
               |   },
               |   {
               |     "type": "Work",
               |     "id": "${works(1).canonicalId}",
               |     "title": "${works(1).title}"
               |   },
               |   {
               |     "type": "Work",
               |     "id": "${works(2).canonicalId}",
               |     "title": "${works(2).title}"
               |   }
               |  ]
               |}
          """.stripMargin
          )
        }
    }
  }

  it("returns a single work when requested with id") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val work = createIdentifiedWork

        insertIntoElasticsearch(indexV2, work)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works/${work.canonicalId}",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               | ${singleWorkResult(apiPrefix)},
               | "id": "${work.canonicalId}",
               | "title": "${work.title}"
               |}
          """.stripMargin
          )
        }
    }
  }

  it(
    "returns the requested page of results when requested with page & pageSize") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val works = createIdentifiedWorks(count = 3).sortBy { _.canonicalId }

        insertIntoElasticsearch(indexV2, works: _*)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?page=2&pageSize=1",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(
                                apiPrefix,
                                pageSize = 1,
                                totalPages = 3,
                                totalResults = 3)},
               |  "prevPage": "$apiScheme://$apiHost/$apiPrefix/works?page=1&pageSize=1",
               |  "nextPage": "$apiScheme://$apiHost/$apiPrefix/works?page=3&pageSize=1",
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${works(1).canonicalId}",
               |     "title": "${works(1).title}"
               |   }]
               |   }
               |  ]
               |}
          """.stripMargin
          )

          server.httpGet(
            path = s"/$apiPrefix/works?page=1&pageSize=1",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(
                                apiPrefix,
                                pageSize = 1,
                                totalPages = 3,
                                totalResults = 3)},
               |  "nextPage": "$apiScheme://$apiHost/$apiPrefix/works?page=2&pageSize=1",
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${works(0).canonicalId}",
               |     "title": "${works(0).title}"
               |   }]
               |   }
               |  ]
               |}
          """.stripMargin
          )

          server.httpGet(
            path = s"/$apiPrefix/works?page=3&pageSize=1",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(
                                apiPrefix,
                                pageSize = 1,
                                totalPages = 3,
                                totalResults = 3)},
               |  "prevPage": "$apiScheme://$apiHost/$apiPrefix/works?page=2&pageSize=1",
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${works(2).canonicalId}",
               |     "title": "${works(2).title}"
               |   }]
               |   }
               |  ]
               |}
          """.stripMargin
          )
        }
    }
  }

  it("ignores parameters that are unused when making an API request") {
    withV2Api {
      case (_, server: EmbeddedHttpServer) =>
        server.httpGet(
          path = s"/$apiPrefix/works?foo=bar",
          andExpect = Status.Ok,
          withJsonBody = emptyJsonResult(apiPrefix)
        )
    }
  }

  it("returns matching results if doing a full-text search") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val work1 = createIdentifiedWorkWith(
          title = "A drawing of a dodo"
        )
        val work2 = createIdentifiedWorkWith(
          title = "A mezzotint of a mouse"
        )
        insertIntoElasticsearch(indexV2, work1, work2)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?query=cat",
            andExpect = Status.Ok,
            withJsonBody = emptyJsonResult(apiPrefix)
          )
        }

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?query=dodo",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(apiPrefix)},
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${work1.canonicalId}",
               |     "title": "${work1.title}"
               |   }
               |  ]
               |}""".stripMargin
          )
        }
    }
  }

  it("searches different indices with the ?_index query parameter") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        withLocalWorksIndex { altIndex =>
          val work = createIdentifiedWork
          insertIntoElasticsearch(indexV2, work)

          val altWork = createIdentifiedWork
          insertIntoElasticsearch(index = altIndex, altWork)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works/${work.canonicalId}",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   | ${singleWorkResult(apiPrefix)},
                   | "id": "${work.canonicalId}",
                   | "title": "${work.title}"
                   |}
          """.stripMargin
            )
          }

          eventually {
            server.httpGet(
              path =
                s"/$apiPrefix/works/${altWork.canonicalId}?_index=${altIndex.name}",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   | ${singleWorkResult(apiPrefix)},
                   | "id": "${altWork.canonicalId}",
                   | "title": "${altWork.title}"
                   |}
          """.stripMargin
            )
          }
        }
    }
  }

  it("looks up works in different indices with the ?_index query parameter") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        withLocalWorksIndex { altIndex =>
          val work = createIdentifiedWorkWith(
            title = "Playing with pangolins"
          )
          insertIntoElasticsearch(indexV2, work)

          val altWork = createIdentifiedWorkWith(
            title = "Playing with pangolins"
          )
          insertIntoElasticsearch(index = altIndex, altWork)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?query=pangolins",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   |  ${resultList(apiPrefix)},
                   |  "results": [
                   |   {
                   |     "type": "Work",
                   |     "id": "${work.canonicalId}",
                   |     "title": "${work.title}"
                   |   }
                   |  ]
                   |}
          """.stripMargin
            )
          }

          eventually {
            server.httpGet(
              path =
                s"/$apiPrefix/works?query=pangolins&_index=${altIndex.name}",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   |  ${resultList(apiPrefix)},
                   |  "results": [
                   |   {
                   |     "type": "Work",
                   |     "id": "${altWork.canonicalId}",
                   |     "title": "${altWork.title}"
                   |   }
                   |  ]
                   |}
          """.stripMargin
            )
          }
        }
    }
  }

  it("shows the thumbnail field if available") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val work = createIdentifiedWorkWith(
          thumbnail = Some(
            DigitalLocation(
              locationType = LocationType("thumbnail-image"),
              url = "https://iiif.example.org/1234/default.jpg",
              license = Some(License_CCBY)
            ))
        )
        insertIntoElasticsearch(indexV2, work)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(apiPrefix)},
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${work.canonicalId}",
               |     "title": "${work.title}",
               |     "thumbnail": ${location(work.thumbnail.get)}
               |    }
               |  ]
               |}
               |
            """.stripMargin
          )
        }
    }
  }

  it("only returns works from the v2 index") {
    withApi {
      case (indexV1, indexV2, server: EmbeddedHttpServer) =>
        val work1 = createIdentifiedWorkWith(
          title = "Working with wombats"
        )
        insertIntoElasticsearch(indexV1, work1)

        val work2 = createIdentifiedWorkWith(
          title = work1.title
        )
        insertIntoElasticsearch(indexV2, work2)

        eventually {
          server.httpGet(
            path = s"/${getApiPrefix(ApiVersions.v2)}/works?query=wombats",
            andExpect = Status.Ok,
            withJsonBody = s"""
                 |{
                 |  ${resultList(apiPrefix)},
                 |  "results": [
                 |   {
                 |     "type": "Work",
                 |     "id": "${work2.canonicalId}",
                 |     "title": "${work2.title}"
                 |   }
                 |  ]
                 |}
          """.stripMargin
          )
        }
    }
  }

  it("supports fetching the workType aggregation") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
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

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?aggregations=workType",
            andExpect = Status.Ok,
            withJsonBody = s"""
                              |{
                              |  ${resultList(apiPrefix, totalResults = 5)},
                              |  "results": [],
                              |  "aggregations": {
                              |  "type" : "Aggregations",
                              |   "workType": {
                              |     "type" : "Aggregation",
                              |     "buckets": [
                              |       {
                              |         "data" : {
                              |           "id" : "a",
                              |           "label" : "Books",
                              |           "type" : "WorkType"
                              |         },
                              |         "count" : 2,
                              |         "type" : "AggregationBucket"
                              |       },
                              |       {
                              |         "data" : {
                              |           "id" : "d",
                              |           "label" : "Journals",
                              |           "type" : "WorkType"
                              |         },
                              |         "count" : 1,
                              |         "type" : "AggregationBucket"
                              |       },
                              |       {
                              |         "data" : {
                              |           "id" : "k",
                              |           "label" : "Pictures",
                              |           "type" : "WorkType"
                              |         },
                              |         "count" : 2,
                              |         "type" : "AggregationBucket"
                              |       }
                              |     ]
                              |   }
                              |  }
                              |}
          """.stripMargin
          )
        }
    }
  }

  it("supports production date sorting") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val work1 = createDatedWork(
          canonicalId = "1",
          dateLabel = "1900"
        )
        val work2 = createDatedWork(
          canonicalId = "2",
          dateLabel = "1976"
        )
        val work3 = createDatedWork(
          canonicalId = "3",
          dateLabel = "1904"
        )
        val work4 = createDatedWork(
          canonicalId = "4",
          dateLabel = "2020"
        )
        val work5 = createDatedWork(
          canonicalId = "5",
          dateLabel = "1098"
        )
        insertIntoElasticsearch(indexV2, work1, work2, work3, work4, work5)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?sort=production.dates.from",
            andExpect = Status.Ok,
            withJsonBody = s"""
                              |{
                              |  ${resultList(apiPrefix, totalResults = 5)},
                              |  "results": [{
	                            |  	 "id": "5",
	                            |  	 "title": "${work5.title}",
	                            |  	 "type": "Work"
	                            |  }, {
	                            |  	 "id": "1",
	                            |  	 "title": "${work1.title}",
	                            |  	 "type": "Work"
	                            |  }, {
	                            |  	 "id": "3",
	                            |  	 "title": "${work3.title}",
	                            |  	 "type": "Work"
	                            |  }, {
	                            |  	 "id": "2",
	                            |  	 "title": "${work2.title}",
	                            |  	 "type": "Work"
	                            |  }, {
	                            |  	 "id": "4",
	                            |  	 "title": "${work4.title}",
	                            |  	 "type": "Work"
	                            |  }]
                              |}
          """.stripMargin
          )
        }
    }
  }

  it("supports sorting of dates in descending order") {
    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        val work1 = createDatedWork(
          canonicalId = "1",
          dateLabel = "1900"
        )
        val work2 = createDatedWork(
          canonicalId = "2",
          dateLabel = "1976"
        )
        val work3 = createDatedWork(
          canonicalId = "3",
          dateLabel = "1904"
        )
        insertIntoElasticsearch(indexV2, work1, work2, work3)

        eventually {
          server.httpGet(
            path =
              s"/$apiPrefix/works?sort=production.dates.from&sortOrder=desc",
            andExpect = Status.Ok,
            withJsonBody = s"""
                              |{
                              |  ${resultList(apiPrefix, totalResults = 3)},
                              |  "results": [{
	                            |  	 "id": "2",
	                            |  	 "title": "${work2.title}",
	                            |  	 "type": "Work"
	                            |  }, {
	                            |  	 "id": "3",
	                            |  	 "title": "${work3.title}",
	                            |  	 "type": "Work"
	                            |  }, {
	                            |  	 "id": "1",
	                            |  	 "title": "${work1.title}",
	                            |  	 "type": "Work"
	                            |  }]
                              |}
          """.stripMargin
          )
        }
    }
  }
}
