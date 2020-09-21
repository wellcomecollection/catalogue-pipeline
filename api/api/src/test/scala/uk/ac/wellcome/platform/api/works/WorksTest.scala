package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal._

class WorksTest extends ApiWorksTestBase {

  it("returns a list of works") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val works = createIdentifiedWorks(count = 3).sortBy {
          _.state.canonicalId
        }

        insertIntoElasticsearch(worksIndex, works: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("returns a single work when requested with id") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val work = createIdentifiedWork

        insertIntoElasticsearch(worksIndex, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.state.canonicalId}") {
          Status.OK -> s"""
            {
             ${singleWorkResult(apiPrefix)},
             "id": "${work.state.canonicalId}",
             "title": "${work.data.title.get}",
             "alternativeTitles": []
            }
          """
        }
    }
  }

  it("returns optional fields when they exist") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val work = createIdentifiedWorkWith(
          duration = Some(3600),
          edition = Some("Special edition"),
        )
        insertIntoElasticsearch(worksIndex, work)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.state.canonicalId}") {
          Status.OK -> s"""
            {
             ${singleWorkResult(apiPrefix)},
             "id": "${work.state.canonicalId}",
             "title": "${work.data.title.get}",
             "alternativeTitles": [],
             "edition": "Special edition",
             "duration": 3600
            }
            """
        }
    }
  }

  it(
    "returns the requested page of results when requested with page & pageSize") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val works = createIdentifiedWorks(count = 3).sortBy {
          _.state.canonicalId
        }

        insertIntoElasticsearch(worksIndex, works: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works?page=2&pageSize=1") {
          Status.OK -> s"""
            {
              ${resultList(
            apiPrefix,
            pageSize = 1,
            totalPages = 3,
            totalResults = 3)},
              "prevPage": "$apiScheme://$apiHost/$apiPrefix/works?page=1&pageSize=1",
              "nextPage": "$apiScheme://$apiHost/$apiPrefix/works?page=3&pageSize=1",
              "results": [
                ${workResponse(works(1))}
              ]
            }
          """
        }

        assertJsonResponse(routes, s"/$apiPrefix/works?page=1&pageSize=1") {
          Status.OK -> s"""
            {
              ${resultList(
            apiPrefix,
            pageSize = 1,
            totalPages = 3,
            totalResults = 3)},
              "nextPage": "$apiScheme://$apiHost/$apiPrefix/works?page=2&pageSize=1",
              "results": [
                ${workResponse(works(0))}
              ]
            }
          """
        }

        assertJsonResponse(routes, s"/$apiPrefix/works?page=3&pageSize=1") {
          Status.OK -> s"""
            {
              ${resultList(
            apiPrefix,
            pageSize = 1,
            totalPages = 3,
            totalResults = 3)},
              "prevPage": "$apiScheme://$apiHost/$apiPrefix/works?page=2&pageSize=1",
              "results": [
                ${workResponse(works(2))}
              ]
            }
          """
        }
    }
  }

  it("ignores parameters that are unused when making an API request") {
    withApi {
      case (_, routes) =>
        assertJsonResponse(routes, s"/$apiPrefix/works?foo=bar") {
          Status.OK -> emptyJsonResult(apiPrefix)
        }
    }
  }

  it("returns matching results if doing a full-text search") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val workDodo = createIdentifiedWorkWith(
          title = Some("A drawing of a dodo")
        )
        val workMouse = createIdentifiedWorkWith(
          title = Some("A mezzotint of a mouse")
        )
        insertIntoElasticsearch(worksIndex, workDodo, workMouse)

        assertJsonResponse(routes, s"/$apiPrefix/works?query=cat") {
          Status.OK -> emptyJsonResult(apiPrefix)
        }

        assertJsonResponse(routes, s"/$apiPrefix/works?query=dodo") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(workDodo))
        }
    }
  }

  it("searches different indices with the ?_index query parameter") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        withLocalWorksIndex { altIndex =>
          val work = createIdentifiedWork
          insertIntoElasticsearch(worksIndex, work)

          val altWork = createIdentifiedWork
          insertIntoElasticsearch(index = altIndex, altWork)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}") {
            Status.OK -> s"""
              {
               ${singleWorkResult(apiPrefix)},
               "id": "${work.state.canonicalId}",
               "title": "${work.data.title.get}",
               "alternativeTitles": []
              }
            """
          }

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${altWork.state.canonicalId}?_index=${altIndex.name}") {
            Status.OK -> s"""
              {
               ${singleWorkResult(apiPrefix)},
               "id": "${altWork.state.canonicalId}",
               "title": "${altWork.data.title.get}",
               "alternativeTitles": []
              }
            """
          }
        }
    }
  }

  it("looks up works in different indices with the ?_index query parameter") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        withLocalWorksIndex { altIndex =>
          val work = createIdentifiedWorkWith(
            title = Some("Playing with pangolins")
          )
          insertIntoElasticsearch(worksIndex, work)

          val altWork = createIdentifiedWorkWith(
            title = Some("Playing with pangolins")
          )
          insertIntoElasticsearch(index = altIndex, altWork)

          assertJsonResponse(routes, s"/$apiPrefix/works?query=pangolins") {
            Status.OK -> worksListResponse(apiPrefix, works = Seq(work))
          }

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=pangolins&_index=${altIndex.name}") {
            Status.OK -> worksListResponse(apiPrefix, works = Seq(altWork))
          }
        }
    }
  }

  it("shows the thumbnail field if available") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        val work = createIdentifiedWorkWith(
          thumbnail = Some(
            DigitalLocationDeprecated(
              locationType = LocationType("thumbnail-image"),
              url = "https://iiif.example.org/1234/default.jpg",
              license = Some(License.CCBY)
            ))
        )
        insertIntoElasticsearch(worksIndex, work)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 1)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work.state.canonicalId}",
                 "title": "${work.data.title.get}",
                 "alternativeTitles": [],
                 "thumbnail": ${location(work.data.thumbnailDeprecated.get)}
                }
              ]
            }
          """
        }
    }
  }

  it("supports production date sorting") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
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
        insertIntoElasticsearch(worksIndex, work1, work2, work3, work4, work5)

        assertJsonResponse(routes, s"/$apiPrefix/works?sort=production.dates") {
          Status.OK -> worksListResponse(
            apiPrefix = apiPrefix,
            works = Seq(work5, work1, work3, work2, work4)
          )
        }
    }
  }

  it("supports sorting of dates in descending order") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
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
        insertIntoElasticsearch(worksIndex, work1, work2, work3)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?sort=production.dates&sortOrder=desc") {
          Status.OK -> worksListResponse(
            apiPrefix = apiPrefix,
            works = Seq(work2, work3, work1)
          )
        }
    }
  }
}
