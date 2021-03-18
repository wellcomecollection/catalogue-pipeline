package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.{
  ItemsGenerators,
  ProductionEventGenerators
}
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.{Work, WorkState}

class WorksTest
    extends ApiWorksTestBase
    with ProductionEventGenerators
    with ItemsGenerators {
  it("returns a list of works") {
    withWorksApi {
      case (worksIndex, routes) =>
        val works = indexedWorks(count = 3).sortBy {
          _.state.canonicalId
        }

        insertIntoElasticsearch(worksIndex, works: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("returns a single work when requested with id") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work = indexedWork()

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
             "availabilities": [${availabilities(work.state.availabilities)}]
            }
          """
        }
    }
  }

  it("returns optional fields when they exist") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work = indexedWork()
          .duration(3600)
          .edition("Special edition")

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
             "availabilities": [${availabilities(work.state.availabilities)}],
             "edition": "Special edition",
             "duration": 3600
            }
            """
        }
    }
  }

  it(
    "returns the requested page of results when requested with page & pageSize") {
    withWorksApi {
      case (worksIndex, routes) =>
        val works = indexedWorks(count = 3).sortBy {
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
    withWorksApi {
      case (_, routes) =>
        assertJsonResponse(routes, s"/$apiPrefix/works?foo=bar") {
          Status.OK -> emptyJsonResult(apiPrefix)
        }
    }
  }

  it("returns matching results if doing a full-text search") {
    withWorksApi {
      case (worksIndex, routes) =>
        val workDodo = indexedWork().title("A drawing of a dodo")
        val workMouse = indexedWork().title("A mezzotint of a mouse")
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
    withWorksApi {
      case (worksIndex, routes) =>
        withLocalWorksIndex { altIndex =>
          val work = indexedWork()
          insertIntoElasticsearch(worksIndex, work)

          val altWork = indexedWork()
          insertIntoElasticsearch(index = altIndex, altWork)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${work.state.canonicalId}") {
            Status.OK -> s"""
              {
               ${singleWorkResult(apiPrefix)},
               "id": "${work.state.canonicalId}",
               "title": "${work.data.title.get}",
               "alternativeTitles": [],
               "availabilities": [${availabilities(work.state.availabilities)}]
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
               "alternativeTitles": [],
               "availabilities": [${availabilities(work.state.availabilities)}]
              }
            """
          }
        }
    }
  }

  it("looks up works in different indices with the ?_index query parameter") {
    withWorksApi {
      case (worksIndex, routes) =>
        withLocalWorksIndex { altIndex =>
          val work = indexedWork().title("Playing with pangolins")
          insertIntoElasticsearch(worksIndex, work)

          val altWork = indexedWork().title("Playing with pangolins")
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
    withWorksApi {
      case (worksIndex, routes) =>
        val thumbnailLocation = DigitalLocation(
          locationType = LocationType.ThumbnailImage,
          url = "https://iiif.example.org/1234/default.jpg",
          license = Some(License.CCBY)
        )
        val work = indexedWork()
          .thumbnail(thumbnailLocation)
          .items(
            List(createIdentifiedItemWith(locations = List(thumbnailLocation))))
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
                 "availabilities": [${availabilities(work.state.availabilities)}],
                 "thumbnail": ${location(work.data.thumbnail.get)}
                }
              ]
            }
          """
        }
    }
  }

  def createDatedWork(canonicalId: String,
                      dateLabel: String): Work.Visible[WorkState.Indexed] =
    indexedWork(canonicalId = canonicalId)
      .production(List(createProductionEventWith(dateLabel = Some(dateLabel))))

  it("supports sorting by production date") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work1 = createDatedWork(canonicalId = "1", dateLabel = "1900")
        val work2 = createDatedWork(canonicalId = "2", dateLabel = "1976")
        val work3 = createDatedWork(canonicalId = "3", dateLabel = "1904")
        val work4 = createDatedWork(canonicalId = "4", dateLabel = "2020")
        val work5 = createDatedWork(canonicalId = "5", dateLabel = "1098")
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
    withWorksApi {
      case (worksIndex, routes) =>
        val work1 = createDatedWork(canonicalId = "1", dateLabel = "1900")
        val work2 = createDatedWork(canonicalId = "2", dateLabel = "1976")
        val work3 = createDatedWork(canonicalId = "3", dateLabel = "1904")
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
