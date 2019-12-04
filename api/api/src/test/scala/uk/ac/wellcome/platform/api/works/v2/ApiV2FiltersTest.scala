package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal.WorkType.{Books, CDRoms, ManuscriptsAsian}
import uk.ac.wellcome.models.work.internal._

import scala.util.Random

class ApiV2FiltersTest extends ApiV2WorksTestBase {

  describe("listing works") {
    it("ignores works with no workType") {
      withApi {
        case (indexV2, routes) =>
          val noWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(workType = None)
          }
          val matchingWork = createIdentifiedWorkWith(
            workType = Some(ManuscriptsAsian))

          val works = noWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?workType=b") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork.canonicalId}",
                    "title": "${matchingWork.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork.data.workType.get)}
                  }
                ]
              }
            """
          }
      }
    }

    it("filters out works with a different workType") {
      withApi {
        case (indexV2, routes) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              workType = Some(CDRoms))
          }
          val matchingWork = createIdentifiedWorkWith(
            workType = Some(ManuscriptsAsian))

          val works = wrongWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?workType=b") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork.canonicalId}",
                    "title": "${matchingWork.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork.data.workType.get)}
                  }
                ]
              }
            """
          }
      }
    }

    it("can filter by multiple workTypes") {
      withApi {
        case (indexV2, routes) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              workType = Some(CDRoms))
          }
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            workType = Some(ManuscriptsAsian))
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            workType = Some(Books))

          val works = wrongWorkTypeWorks :+ matchingWork1 :+ matchingWork2
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?workType=a,b") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork1.canonicalId}",
                    "title": "${matchingWork1.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork1.data.workType.get)}
                  },
                  {
                    "type": "Work",
                    "id": "${matchingWork2.canonicalId}",
                    "title": "${matchingWork2.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork2.data.workType.get)}
                  }
                ]
              }
            """
          }
      }
    }

    it("filters by item LocationType") {
      withApi {
        case (indexV2, routes) =>
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

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.locationType=iiif-image,digit&include=items") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork1.canonicalId}",
                    "title": "${matchingWork1.data.title.get}",
                    "alternativeTitles": [],
                    "items": [${items(matchingWork1.data.items)}]
                  },
                  {
                    "type": "Work",
                    "id": "${matchingWork2.canonicalId}",
                    "title": "${matchingWork2.data.title.get}",
                    "alternativeTitles": [],
                    "items": [${items(matchingWork2.data.items)}]
                  }
                ]
              }
            """
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
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.from=1900-01-01&production.dates.to=1960-01-01") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${work2.canonicalId}",
                    "title": "${work2.data.title.get}",
                    "alternativeTitles": []
                  }
                ]
              }
            """
          }
      }
    }

    it("filters by from date") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.from=1900-01-01") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${work2.canonicalId}",
                    "title": "${work2.data.title.get}",
                    "alternativeTitles": []
                  },
                  {
                    "type": "Work",
                    "id": "${work3.canonicalId}",
                    "title": "${work3.data.title.get}",
                    "alternativeTitles": []
                  }
                ]
              }
            """
          }
      }
    }

    it("filters by to date") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.to=1960-01-01") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${work1.canonicalId}",
                    "title": "${work1.data.title.get}",
                    "alternativeTitles": []
                  },
                  {
                    "type": "Work",
                    "id": "${work2.canonicalId}",
                    "title": "${work2.data.title.get}",
                    "alternativeTitles": []
                  }
                ]
              }
            """
          }
      }
    }

    it("errors on invalid date") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, work1, work2, work3)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.from=1900-01-01&production.dates.to=INVALID") {
            Status.BadRequest ->
              badRequest(
                apiPrefix,
                "production.dates.to: Invalid date encoding. Expected YYYY-MM-DD"
              )
          }
      }
    }
  }

  describe("searching works") {

    it("ignores works with no workType") {
      withApi {
        case (indexV2, routes) =>
          val noWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              title = Some("Amazing aubergines"),
              workType = None)
          }
          val matchingWork = createIdentifiedWorkWith(
            title = Some("Amazing aubergines"),
            workType = Some(ManuscriptsAsian))

          val works = noWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=aubergines&workType=b") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork.canonicalId}",
                    "title": "${matchingWork.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork.data.workType.get)}
                  }
                ]
              }
            """
          }
      }
    }

    it("filters out works with a different workType") {
      withApi {
        case (indexV2, routes) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              title = Some("Bouncing bananas"),
              workType = Some(CDRoms))
          }
          val matchingWork = createIdentifiedWorkWith(
            title = Some("Bouncing bananas"),
            workType = Some(ManuscriptsAsian))

          val works = wrongWorkTypeWorks :+ matchingWork
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=bananas&workType=b") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork.canonicalId}",
                    "title": "${matchingWork.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork.data.workType.get)}
                  }
                ]
              }
            """
          }
      }
    }

    it("can filter by multiple workTypes") {
      withApi {
        case (indexV2, routes) =>
          val wrongWorkTypeWorks = (1 to 3).map { _ =>
            createIdentifiedWorkWith(
              title = Some("Bouncing bananas"),
              workType = Some(CDRoms))
          }
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            title = Some("Bouncing bananas"),
            workType = Some(ManuscriptsAsian))
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            title = Some("Bouncing bananas"),
            workType = Some(Books))

          val works = wrongWorkTypeWorks :+ matchingWork1 :+ matchingWork2
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=bananas&workType=a,b") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork1.canonicalId}",
                    "title": "${matchingWork1.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork1.data.workType.get)}
                  },
                  {
                    "type": "Work",
                    "id": "${matchingWork2.canonicalId}",
                    "title": "${matchingWork2.data.title.get}",
                    "alternativeTitles": [],
                    "workType": ${workType(matchingWork2.data.workType.get)}
                  }
                ]
              }
            """
          }
      }
    }

    it("filters by item LocationType") {
      withApi {
        case (indexV2, routes) =>
          val noItemWorks = createIdentifiedWorks(count = 3)
          val matchingWork1 = createIdentifiedWorkWith(
            canonicalId = "001",
            title = Some("Crumbling carrots"),
            items = List(
              createItemWithLocationType(LocationType("iiif-image"))
            )
          )
          val matchingWork2 = createIdentifiedWorkWith(
            canonicalId = "002",
            title = Some("Crumbling carrots"),
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

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=carrots&items.locations.locationType=iiif-image,digit&include=items") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${matchingWork1.canonicalId}",
                    "title": "${matchingWork1.data.title.get}",
                    "alternativeTitles": [],
                    "items": [${items(matchingWork1.data.items)}]
                  },
                  {
                    "type": "Work",
                    "id": "${matchingWork2.canonicalId}",
                    "title": "${matchingWork2.data.title.get}",
                    "alternativeTitles": [],
                    "items": [${items(matchingWork2.data.items)}]
                  }
                ]
              }
            """
          }
      }
    }
  }

  describe("filtering works by language") {

    val englishWork = createIdentifiedWorkWith(
      canonicalId = "1",
      title = Some("Caterpiller"),
      language = Some(Language("eng", "English"))
    )
    val germanWork = createIdentifiedWorkWith(
      canonicalId = "2",
      title = Some("Ubergang"),
      language = Some(Language("ger", "German"))
    )
    val noLanguageWork = createIdentifiedWorkWith(title = Some("£@@!&$"))
    val works = List(englishWork, germanWork, noLanguageWork)

    it("filters by language") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?language=eng") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${englishWork.canonicalId}",
                    "title": "${englishWork.data.title.get}",
                    "alternativeTitles": [],
                    "language": {
                      "id": "eng",
                      "label": "English",
                      "type": "Language"
                    }
                  }
                ]
              }
            """
          }
      }
    }

    it("filters by multiple comma seperated languages") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?language=eng,ger") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  {
                    "type": "Work",
                    "id": "${englishWork.canonicalId}",
                    "title": "${englishWork.data.title.get}",
                    "alternativeTitles": [],
                    "language": {
                      "id": "eng",
                      "label": "English",
                      "type": "Language"
                    }
                  },
                  {
                    "type": "Work",
                    "id": "${germanWork.canonicalId}",
                    "title": "${germanWork.data.title.get}",
                    "alternativeTitles": [],
                    "language": {
                      "id": "ger",
                      "label": "German",
                      "type": "Language"
                    }
                  }
                ]
              }
            """
          }
      }
    }
  }

  describe("filtering works by genre") {

    val horror = createGenreWith("horrible stuff")
    val romcom = createGenreWith("heartwarming stuff")

    val horrorWork = createIdentifiedWorkWith(
      title = Some("horror"),
      canonicalId = "1",
      genres = List(horror)
    )
    val romcomWork = createIdentifiedWorkWith(
      title = Some("romcom"),
      canonicalId = "2",
      genres = List(romcom)
    )
    val romcomHorrorWork = createIdentifiedWorkWith(
      title = Some("romcom horror"),
      canonicalId = "3",
      genres = List(romcom, horror)
    )
    val noGenreWork = createIdentifiedWorkWith(
      title = Some("no genre"),
      canonicalId = "4"
    )

    val works = List(horrorWork, romcomWork, romcomHorrorWork, noGenreWork)

    it("filters by genre with partial match") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?genres.label=horrible") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  ${workResponse(horrorWork)},
                  ${workResponse(romcomHorrorWork)}
                ]
              }
            """
          }
      }
    }

    it("filters by genre using multiple terms") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?genres.label=horrible%20heartwarming") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  ${workResponse(romcomHorrorWork)}
                ]
              }
            """
          }
      }
    }
  }

  describe("filtering works by subject") {

    val nineteenthCentury = createSubjectWith("19th Century")
    val paris = createSubjectWith("Paris")

    val nineteenthCenturyWork = createIdentifiedWorkWith(
      title = Some("19th century"),
      canonicalId = "1",
      subjects = List(nineteenthCentury)
    )
    val parisWork = createIdentifiedWorkWith(
      title = Some("paris"),
      canonicalId = "2",
      subjects = List(paris)
    )
    val nineteenthCenturyParisWork = createIdentifiedWorkWith(
      title = Some("19th century paris"),
      canonicalId = "3",
      subjects = List(nineteenthCentury, paris)
    )
    val noSubjectWork = createIdentifiedWorkWith(
      title = Some("no subject"),
      canonicalId = "4"
    )

    val works = List(
      nineteenthCenturyWork,
      parisWork,
      nineteenthCenturyParisWork,
      noSubjectWork)

    def workResponse(work: IdentifiedWork): String =
      s"""
        | {
        |   "type": "Work",
        |   "id": "${work.canonicalId}",
        |   "title": "${work.data.title.get}",
        |   "alternativeTitles": []
        | }
      """.stripMargin

    it("filters by subjects") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?subjects.label=paris") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  ${workResponse(parisWork)},
                  ${workResponse(nineteenthCenturyParisWork)}
                ]
              }
            """
          }
      }
    }

    it("filters by subjects using multiple terms") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?subjects.label=19th%20century%20paris") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 1)},
                "results": [
                  ${workResponse(nineteenthCenturyParisWork)}
                ]
              }
            """
          }
      }
    }
  }

  describe("filtering works by license") {

    val ccByWork = createLicensedWork("A", List(License_CCBY))
    val ccByNcWork = createLicensedWork("B", List(License_CCBYNC))
    val bothLicenseWork =
      createLicensedWork("C", List(License_CCBY, License_CCBYNC))
    val noLicenseWork = createLicensedWork("D", Nil)

    val works = List(ccByWork, ccByNcWork, bothLicenseWork, noLicenseWork)

    it("filters by license") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?license=cc-by") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 2)},
                "results": [
                  ${workResponse(ccByWork)},
                  ${workResponse(bothLicenseWork)}
                ]
              }
            """
          }
      }
    }

    it("filters by multiple licenses") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?license=cc-by,cc-by-nc") {
            Status.OK -> s"""
              {
                ${resultList(apiPrefix, totalResults = 3)},
                "results": [
                  ${workResponse(ccByWork)},
                  ${workResponse(ccByNcWork)},
                  ${workResponse(bothLicenseWork)}
                ]
              }
            """
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
