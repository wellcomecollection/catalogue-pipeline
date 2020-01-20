package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal.WorkType.{
  Books,
  CDRoms,
  ManuscriptsAsian
}
import uk.ac.wellcome.models.work.internal._

import scala.util.Random

class ApiV2FiltersTest extends ApiV2WorksTestBase {
  it("combines multiple filters") {
    val work1 = createIdentifiedWorkWith(
      genres = List(createGenreWith(label = "horror")),
      subjects = List(createSubjectWith(label = "france"))
    )
    val work2 = createIdentifiedWorkWith(
      genres = List(createGenreWith(label = "horror")),
      subjects = List(createSubjectWith(label = "england"))
    )
    val work3 = createIdentifiedWorkWith(
      genres = List(createGenreWith(label = "fantasy")),
      subjects = List(createSubjectWith(label = "england"))
    )

    val works = Seq(work1, work2, work3)

    withApi {
      case (indexV2, routes) =>
        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?genres.label=horror&subjects.label=england") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(work2))
        }
    }
  }

  describe("filtering works by item LocationType") {
    def createItemWithLocationType(
      locationType: LocationType): Item[Minted] =
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

    val worksWithNoItem = createIdentifiedWorks(count = 3)

    val work1 = createIdentifiedWorkWith(
      canonicalId = "1",
      title = Some("Crumbling carrots"),
      items = List(
        createItemWithLocationType(LocationType("iiif-image"))
      )
    )
    val work2 = createIdentifiedWorkWith(
      canonicalId = "2",
      title = Some("Crumbling carrots"),
      items = List(
        createItemWithLocationType(LocationType("digit")),
        createItemWithLocationType(LocationType("dimgs"))
      )
    )
    val work3 = createIdentifiedWorkWith(
      items = List(
        createItemWithLocationType(LocationType("dpoaa"))
      )
    )

    val works = worksWithNoItem ++ Seq(work1, work2, work3)

    it("when listing works") {
      withApi {
        case (indexV2, routes) =>
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
                  "id": "${work1.canonicalId}",
                  "title": "${work1.data.title.get}",
                  "alternativeTitles": [],
                  "items": [${items(work1.data.items)}]
                },
                {
                  "type": "Work",
                  "id": "${work2.canonicalId}",
                  "title": "${work2.data.title.get}",
                  "alternativeTitles": [],
                  "items": [${items(work2.data.items)}]
                }
              ]
            }
          """
          }
      }
    }

    it("when searching works") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=carrots&items.locations.locationType=digit&include=items") {
            Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 1)},
              "results": [
                {
                  "type": "Work",
                  "id": "${work2.canonicalId}",
                  "title": "${work2.data.title.get}",
                  "alternativeTitles": [],
                  "items": [${items(work2.data.items)}]
                }
              ]
            }
          """
          }
      }
    }
  }

  describe("filtering works by WorkType") {
    val noWorkTypeWorks = (1 to 3).map { _ =>
      createIdentifiedWorkWith(workType = None)
    }

    // We assign explicit canonical IDs to ensure stable ordering when listing
    val bookWork = createIdentifiedWorkWith(
      title = Some("apple apple apple"),
      canonicalId = "book1",
      workType = Some(Books)
    )
    val cdRomWork = createIdentifiedWorkWith(
      title = Some("apple apple"),
      canonicalId = "cdrom1",
      workType = Some(CDRoms)
    )
    val manuscriptWork = createIdentifiedWorkWith(
      title = Some("apple"),
      canonicalId = "manuscript1",
      workType = Some(ManuscriptsAsian)
    )

    val works = noWorkTypeWorks ++ Seq(bookWork, cdRomWork, manuscriptWork)

    it("when listing works") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?workType=${ManuscriptsAsian.id}") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(manuscriptWork))
          }
      }
    }

    it("filters by multiple workTypes") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?workType=${ManuscriptsAsian.id},${CDRoms.id}") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(cdRomWork, manuscriptWork))
          }
      }
    }

    it("when searching works") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=apple&workType=${ManuscriptsAsian.id},${CDRoms.id}") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(cdRomWork, manuscriptWork))
          }
      }
    }
  }

  describe("filtering works by date range") {
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
            Status.OK -> worksListResponse(apiPrefix, works = Seq(work2))
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
            Status.OK -> worksListResponse(apiPrefix, works = Seq(work2, work3))
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
            Status.OK -> worksListResponse(apiPrefix, works = Seq(work1, work2))
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
            Status.OK -> worksListResponse(apiPrefix, works = Seq(englishWork))
          }
      }
    }

    it("filters by multiple comma seperated languages") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?language=eng,ger") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(englishWork, germanWork))
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
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(horrorWork, romcomHorrorWork))
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
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(romcomHorrorWork))
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

    it("filters by subjects") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?subjects.label=paris") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(parisWork, nineteenthCenturyParisWork)
            )
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
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(nineteenthCenturyParisWork)
            )
          }
      }
    }
  }

  describe("filtering works by license") {
    val ccByWork = createLicensedWork("A", List(License.CCBY))
    val ccByNcWork = createLicensedWork("B", List(License.CCBYNC))
    val bothLicenseWork =
      createLicensedWork("C", List(License.CCBY, License.CCBYNC))
    val noLicenseWork = createLicensedWork("D", Nil)

    val works = List(ccByWork, ccByNcWork, bothLicenseWork, noLicenseWork)

    it("filters by license") {
      withApi {
        case (indexV2, routes) =>
          insertIntoElasticsearch(indexV2, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?license=cc-by") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(ccByWork, bothLicenseWork)
            )
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
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(ccByWork, ccByNcWork, bothLicenseWork)
            )
          }
      }
    }
  }
}
