package uk.ac.wellcome.platform.api.works.v2

import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import uk.ac.wellcome.models.work.internal._

import scala.util.Random

class ApiV2FiltersTest extends ApiV2WorksTestBase {

  describe("listing works") {
    it("ignores works with no workType") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val noWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(workType = None)
          }
          val matchingWork = createIdentifiedWorkWith(
            workType = Some(WorkType(id = "b", label = "Books")))

          val works = noWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?workType=b",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   |  ${resultList(apiPrefix, totalResults = 1)},
                   |  "results": [
                   |    {
                   |      "type": "Work",
                   |      "id": "${matchingWork.canonicalId}",
                   |      "title": "${matchingWork.title}",
                   |      "workType": ${workType(matchingWork.workType.get)}
                   |    }
                   |  ]
                   |}
          """.stripMargin
            )
          }
      }
    }

    it("filters out works with a different workType") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              workType = Some(WorkType(id = "m", label = "Manuscripts")))
          }
          val matchingWork = createIdentifiedWorkWith(
            workType = Some(WorkType(id = "b", label = "Books")))

          val works = wrongWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?workType=b",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   |  ${resultList(apiPrefix, totalResults = 1)},
                   |  "results": [
                   |    {
                   |      "type": "Work",
                   |      "id": "${matchingWork.canonicalId}",
                   |      "title": "${matchingWork.title}",
                   |      "workType": ${workType(matchingWork.workType.get)}
                   |    }
                   |  ]
                   |}
          """.stripMargin
            )
          }
      }
    }

    it("can filter by multiple workTypes") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              workType = Some(WorkType(id = "m", label = "Manuscripts")))
          }
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            workType = Some(WorkType(id = "b", label = "Books")))
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            workType = Some(WorkType(id = "a", label = "Archives")))

          val works = wrongWorkTypeWorks :+ matchingWork1 :+ matchingWork2
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?workType=a,b",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 2)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork1.canonicalId}",
                                |      "title": "${matchingWork1.title}",
                                |      "workType": ${workType(
                                  matchingWork1.workType.get)}
                                |    },
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork2.canonicalId}",
                                |      "title": "${matchingWork2.title}",
                                |      "workType": ${workType(
                                  matchingWork2.workType.get)}
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }

    it("filters by item LocationType") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val noItemWorks = createIdentifiedWorks(count = 3)
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            items = List(
              createItemWithLocationType(LocationType("iiif-image"))
            )
          )
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            items = List(
              createItemWithLocationType(LocationType("digit")),
              createItemWithLocationType(LocationType("dimgs"))
            )
          )
          val wrongLocationTypeWork = createIdentifiedWorkWith(
            items = List(
              createItemWithLocationType(LocationType("dpoaa"))
            )
          )

          val works = noItemWorks :+ matchingWork1 :+ matchingWork2 :+ wrongLocationTypeWork
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path =
                s"/$apiPrefix/works?items.locations.locationType=iiif-image,digit&include=items",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 2)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork1.canonicalId}",
                                |      "title": "${matchingWork1.title}",
                                |      "items": [${items(matchingWork1.items)}]
                                |    },
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork2.canonicalId}",
                                |      "title": "${matchingWork2.title}",
                                |      "items": [${items(matchingWork2.items)}]
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }
  }

  describe("filtering works by date") {

    val (work1, work2, work3) = (
      createDatedWork("1709", canonicalId = "a"),
      createDatedWork("1950", canonicalId = "b"),
      createDatedWork("2000", canonicalId = "c")
    )

    it("filters by date range") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          eventually {
            server.httpGet(
              path =
                s"/$apiPrefix/works?production.dates.from=1900-01-01&production.dates.to=1960-01-01",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 1)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${work2.canonicalId}",
                                |      "title": "${work2.title}"
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }

    it("filters by from date") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?production.dates.from=1900-01-01",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 2)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${work2.canonicalId}",
                                |      "title": "${work2.title}"
                                |    },
                                |    {
                                |      "type": "Work",
                                |      "id": "${work3.canonicalId}",
                                |      "title": "${work3.title}"
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }

    it("filters by to date") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?production.dates.to=1960-01-01",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 2)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${work1.canonicalId}",
                                |      "title": "${work1.title}"
                                |    },
                                |    {
                                |      "type": "Work",
                                |      "id": "${work2.canonicalId}",
                                |      "title": "${work2.title}"
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }

    it("filters when work has a broader date range than query") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val work = createDatedWork("1000-1100", canonicalId = "d")
          insertIntoElasticsearch(indexV2, work, work1, work2, work3)
          eventually {
            server.httpGet(
              s"/$apiPrefix/works?production.dates.from=1066-01-01&production.dates.to=1066-12-31",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 1)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${work.canonicalId}",
                                |      "title": "${work.title}"
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }

    it("errors on invalid date") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          eventually {
            server.httpGet(
              path =
                s"/$apiPrefix/works?production.dates.from=1900-01-01&production.dates.to=INVALID",
              andExpect = Status.BadRequest,
            )
          }
      }
    }
  }

  describe("searching works") {
    it("ignores works with no workType") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val noWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              title = "Amazing aubergines",
              workType = None)
          }
          val matchingWork = createIdentifiedWorkWith(
            title = "Amazing aubergines",
            workType = Some(WorkType(id = "b", label = "Books")))

          val works = noWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?query=aubergines&workType=b",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   |  ${resultList(apiPrefix, totalResults = 1)},
                   |  "results": [
                   |    {
                   |      "type": "Work",
                   |      "id": "${matchingWork.canonicalId}",
                   |      "title": "${matchingWork.title}",
                   |      "workType": ${workType(matchingWork.workType.get)}
                   |    }
                   |  ]
                   |}
          """.stripMargin
            )
          }
      }
    }

    it("filters out works with a different workType") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              title = "Bouncing bananas",
              workType = Some(WorkType(id = "m", label = "Manuscripts")))
          }
          val matchingWork = createIdentifiedWorkWith(
            title = "Bouncing bananas",
            workType = Some(WorkType(id = "b", label = "Books")))

          val works = wrongWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?query=bananas&workType=b",
              andExpect = Status.Ok,
              withJsonBody = s"""
                   |{
                   |  ${resultList(apiPrefix, totalResults = 1)},
                   |  "results": [
                   |    {
                   |      "type": "Work",
                   |      "id": "${matchingWork.canonicalId}",
                   |      "title": "${matchingWork.title}",
                   |      "workType": ${workType(matchingWork.workType.get)}
                   |    }
                   |  ]
                   |}
          """.stripMargin
            )
          }
      }
    }

    it("can filter by multiple workTypes") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              title = "Bouncing bananas",
              workType = Some(WorkType(id = "m", label = "Manuscripts")))
          }
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            title = "Bouncing bananas",
            workType = Some(WorkType(id = "b", label = "Books")))
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            title = "Bouncing bananas",
            workType = Some(WorkType(id = "a", label = "Archives")))

          val works = wrongWorkTypeWorks :+ matchingWork1 :+ matchingWork2
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path = s"/$apiPrefix/works?query=bananas&workType=a,b",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 2)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork1.canonicalId}",
                                |      "title": "${matchingWork1.title}",
                                |      "workType": ${workType(
                                  matchingWork1.workType.get)}
                                |    },
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork2.canonicalId}",
                                |      "title": "${matchingWork2.title}",
                                |      "workType": ${workType(
                                  matchingWork2.workType.get)}
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }

    it("filters by item LocationType") {
      withV2Api {
        case (indexV2, server: EmbeddedHttpServer) =>
          val noItemWorks = createIdentifiedWorks(count = 3)
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            title = "Crumbling carrots",
            items = List(
              createItemWithLocationType(LocationType("iiif-image"))
            )
          )
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            title = "Crumbling carrots",
            items = List(
              createItemWithLocationType(LocationType("digit")),
              createItemWithLocationType(LocationType("dimgs"))
            )
          )
          val wrongLocationTypeWork = createIdentifiedWorkWith(
            items = List(
              createItemWithLocationType(LocationType("dpoaa"))
            )
          )

          val works = noItemWorks :+ matchingWork1 :+ matchingWork2 :+ wrongLocationTypeWork
          insertIntoElasticsearch(indexV2, works: _*)

          eventually {
            server.httpGet(
              path =
                s"/$apiPrefix/works?query=carrots&items.locations.locationType=iiif-image,digit&include=items",
              andExpect = Status.Ok,
              withJsonBody = s"""
                                |{
                                |  ${resultList(apiPrefix, totalResults = 2)},
                                |  "results": [
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork1.canonicalId}",
                                |      "title": "${matchingWork1.title}",
                                |      "items": [${items(matchingWork1.items)}]
                                |    },
                                |    {
                                |      "type": "Work",
                                |      "id": "${matchingWork2.canonicalId}",
                                |      "title": "${matchingWork2.title}",
                                |      "items": [${items(matchingWork2.items)}]
                                |    }
                                |  ]
                                |}
          """.stripMargin
            )
          }
      }
    }
  }

  private def createItemWithLocationType(
    locationType: LocationType): Identified[Item] =
    createIdentifiedItemWith(
      locations = List(
        // This test really shouldn't be affected by physical/digital locations;
        // we just pick randomly here to ensure we get a good mixture.
        Random
          .shuffle(
            List(
              createPhysicalLocationWith(locationType = locationType),
              createDigitalLocationWith(locationType = locationType)
            ))
          .head
      )
    )
}
