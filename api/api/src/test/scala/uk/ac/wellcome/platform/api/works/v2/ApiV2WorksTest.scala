package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal._

class ApiV2WorksTest extends ApiV2WorksTestBase {

  it("returns a list of works") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 3).sortBy { _.canonicalId }

        insertIntoElasticsearch(indexV2, works: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("returns a single work when requested with id") {
    withApi {
      case (indexV2, routes) =>
        val work = createIdentifiedWork

        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(routes, s"/$apiPrefix/works/${work.canonicalId}") {
          Status.OK -> s"""
            {
             ${singleWorkResult(apiPrefix)},
             "id": "${work.canonicalId}",
             "title": "${work.data.title.get}",
             "alternativeTitles": []
            }
          """
        }
    }
  }

  it("returns optional fields when they exist") {
    withApi {
      case (indexV2, routes) =>
        val work = createIdentifiedWorkWith(
          duration = Some(3600),
          edition = Some("Special edition"),
        )
        insertIntoElasticsearch(indexV2, work)
        assertJsonResponse(routes, s"/$apiPrefix/works/${work.canonicalId}") {
          Status.OK -> s"""
            {
             ${singleWorkResult(apiPrefix)},
             "id": "${work.canonicalId}",
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
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 3).sortBy { _.canonicalId }

        insertIntoElasticsearch(indexV2, works: _*)

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
      case (indexV2, routes) =>
        assertJsonResponse(routes, s"/$apiPrefix/works?foo=bar") {
          Status.OK -> emptyJsonResult(apiPrefix)
        }
    }
  }

  it("returns matching results if doing a full-text search") {
    withApi {
      case (indexV2, routes) =>
        val workDodo = createIdentifiedWorkWith(
          title = Some("A drawing of a dodo")
        )
        val workMouse = createIdentifiedWorkWith(
          title = Some("A mezzotint of a mouse")
        )
        insertIntoElasticsearch(indexV2, workDodo, workMouse)

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
      case (indexV2, routes) =>
        withLocalWorksIndex { altIndex =>
          val work = createIdentifiedWork
          insertIntoElasticsearch(indexV2, work)

          val altWork = createIdentifiedWork
          insertIntoElasticsearch(index = altIndex, altWork)

          assertJsonResponse(routes, s"/$apiPrefix/works/${work.canonicalId}") {
            Status.OK -> s"""
              {
               ${singleWorkResult(apiPrefix)},
               "id": "${work.canonicalId}",
               "title": "${work.data.title.get}",
               "alternativeTitles": []
              }
            """
          }

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works/${altWork.canonicalId}?_index=${altIndex.name}") {
            Status.OK -> s"""
              {
               ${singleWorkResult(apiPrefix)},
               "id": "${altWork.canonicalId}",
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
      case (indexV2, routes) =>
        withLocalWorksIndex { altIndex =>
          val work = createIdentifiedWorkWith(
            title = Some("Playing with pangolins")
          )
          insertIntoElasticsearch(indexV2, work)

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
      case (indexV2, routes) =>
        val work = createIdentifiedWorkWith(
          thumbnail = Some(
            DigitalLocation(
              locationType = LocationType("thumbnail-image"),
              url = "https://iiif.example.org/1234/default.jpg",
              license = Some(License.CCBY)
            ))
        )
        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 1)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work.canonicalId}",
                 "title": "${work.data.title.get}",
                 "alternativeTitles": [],
                 "thumbnail": ${location(work.data.thumbnail.get)}
                }
              ]
            }
          """
        }
    }
  }

  it("supports production date sorting") {
    withApi {
      case (indexV2, routes) =>
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
      case (indexV2, routes) =>
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

  it("supports filtering on collection") {
    withApi {
      case (indexV2, routes) =>
        val work1Archive1Depth1 = createIdentifiedWorkWith(
          canonicalId = "work1Archive1Depth1",
          collectionPath = Some(CollectionPath("archive1", CollectionLevel.Collection))
        )
        val work2Archive1Depth2 = createIdentifiedWorkWith(
          canonicalId = "work2Archive1Depth2",
          collectionPath =
            Some(CollectionPath("archive1/depth2", CollectionLevel.Series))
        )
        val work3Archive1Depth3 = createIdentifiedWorkWith(
          canonicalId = "work3Archive1Depth3",
          collectionPath =
            Some(CollectionPath("archive1/depth2/depth3", CollectionLevel.Item))
        )
        val work4Archive2Depth1 = createIdentifiedWorkWith(
          canonicalId = "work4Archive2Depth1",
          collectionPath = Some(CollectionPath("archive2", CollectionLevel.Collection))
        )
        val work5Archive2Depth2 = createIdentifiedWorkWith(
          canonicalId = "work5Archive2Depth2",
          collectionPath =
            Some(CollectionPath("archive2/depth2", CollectionLevel.Series))
        )
        val work6Archive2Depth3 = createIdentifiedWorkWith(
          canonicalId = "work6Archive2Depth3",
          collectionPath =
            Some(CollectionPath("archive2/depth2/depth3", CollectionLevel.Item))
        )
        val work7Archiveless = createIdentifiedWorkWith(
          canonicalId = "work7Archiveless",
        )

        insertIntoElasticsearch(
          indexV2,
          work1Archive1Depth1,
          work2Archive1Depth2,
          work3Archive1Depth3,
          work4Archive2Depth1,
          work5Archive2Depth2,
          work6Archive2Depth3,
          work7Archiveless)

        withClue(
          "Single depth collection returns everything in that collection") {
          assertJsonResponse(routes, s"/$apiPrefix/works?collection=archive1") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(
                work1Archive1Depth1,
                work2Archive1Depth2,
                work3Archive1Depth3)
            )
          }

          assertJsonResponse(routes, s"/$apiPrefix/works?collection=archive2") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(
                work4Archive2Depth1,
                work5Archive2Depth2,
                work6Archive2Depth3)
            )
          }
        }

        withClue(
          "Multi depth collection returns everything within that section") {
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?collection=archive1/depth2") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work2Archive1Depth2, work3Archive1Depth3)
            )
          }

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?collection=archive1/depth2/depth3") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work3Archive1Depth3)
            )
          }
        }
    }
  }

  it("supports filtering collection on depth") {
    withApi {
      case (indexV2, routes) =>
        val work1Depth1 = createIdentifiedWorkWith(
          canonicalId = "1",
          collectionPath = Some(CollectionPath("/depth1", CollectionLevel.Collection))
        )
        val work2Depth1 = createIdentifiedWorkWith(
          canonicalId = "2",
          collectionPath = Some(CollectionPath("/depth1", CollectionLevel.Collection))
        )
        val work3Depth2 = createIdentifiedWorkWith(
          canonicalId = "3",
          collectionPath =
            Some(CollectionPath("/depth1/depth2", CollectionLevel.Series))
        )
        val work4Depth3 = createIdentifiedWorkWith(
          canonicalId = "4",
          collectionPath =
            Some(CollectionPath("/depth1/depth2/depth3", CollectionLevel.Item))
        )
        val work5Depth3 = createIdentifiedWorkWith(
          canonicalId = "5",
          collectionPath =
            Some(CollectionPath("/depth1/depth2/depth3", CollectionLevel.Item))
        )
        val work6NoDepth = createIdentifiedWorkWith(
          canonicalId = "6",
          collectionPath = None
        )
        insertIntoElasticsearch(
          indexV2,
          work1Depth1,
          work2Depth1,
          work3Depth2,
          work4Depth3,
          work5Depth3,
          work6NoDepth)

        assertJsonResponse(routes, s"/$apiPrefix/works?collection.depth=1") {
          Status.OK -> worksListResponse(
            apiPrefix = apiPrefix,
            works = Seq(work1Depth1, work2Depth1)
          )
        }

        assertJsonResponse(routes, s"/$apiPrefix/works?collection.depth=2") {
          Status.OK -> worksListResponse(
            apiPrefix = apiPrefix,
            works = Seq(work3Depth2)
          )
        }

        assertJsonResponse(routes, s"/$apiPrefix/works?collection.depth=3") {
          Status.OK -> worksListResponse(
            apiPrefix = apiPrefix,
            works = Seq(work4Depth3, work5Depth3)
          )
        }
    }
  }
}
