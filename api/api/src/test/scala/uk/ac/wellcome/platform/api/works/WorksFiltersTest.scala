package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal.Format.{
  Books,
  CDRoms,
  ManuscriptsAsian
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.{
  ItemsGenerators,
  ProductionEventGenerators
}

class WorksFiltersTest
    extends ApiWorksTestBase
    with ItemsGenerators
    with ProductionEventGenerators {
  it("combines multiple filters") {
    val work1 = indexedWork()
      .genres(List(createGenreWith(label = "horror")))
      .subjects(List(createSubjectWith(label = "france")))
    val work2 = indexedWork()
      .genres(List(createGenreWith(label = "horror")))
      .subjects(List(createSubjectWith(label = "england")))
    val work3 = indexedWork()
      .genres(List(createGenreWith(label = "fantasy")))
      .subjects(List(createSubjectWith(label = "england")))

    val works = Seq(work1, work2, work3)

    withWorksApi {
      case (worksIndex, routes) =>
        insertIntoElasticsearch(worksIndex, works: _*)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works?genres.label=horror&subjects.label=england") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(work2))
        }
    }
  }

  describe("filtering by item Location type") {
    val digitalWorks = (1 to 2).map { _ =>
      indexedWork()
        .title("An example digital work")
        .items(
          List(
            createIdentifiedItemWith(locations = List(createDigitalLocation)))
        )
    }

    val physicalWorks = (1 to 2).map { _ =>
      indexedWork()
        .title("An example physical work")
        .items(
          List(
            createIdentifiedItemWith(locations = List(createPhysicalLocation)))
        )
    }

    val comboWorks = (1 to 2).map { _ =>
      indexedWork()
        .title("An example combo work")
        .items(
          List(
            createIdentifiedItemWith(
              locations = List(createPhysicalLocation, createDigitalLocation)))
        )
    }

    val works = digitalWorks ++ physicalWorks ++ comboWorks

    it("filters by PhysicalLocation when listing") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = physicalWorks ++ comboWorks

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.type=PhysicalLocation") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = matchingWorks.sortBy { _.state.canonicalId }
            )
          }
      }
    }

    it("filters by PhysicalLocation when searching") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = physicalWorks ++ comboWorks

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.type=PhysicalLocation&query=example") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = matchingWorks.sortBy { _.state.canonicalId }
            )
          }
      }
    }

    it("filters by DigitalLocation when listing") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = digitalWorks ++ comboWorks

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.type=DigitalLocation") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = matchingWorks.sortBy { _.state.canonicalId }
            )
          }
      }
    }

    it("filters by DigitalLocation when searching") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = digitalWorks ++ comboWorks

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.type=DigitalLocation&query=example") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = matchingWorks.sortBy { _.state.canonicalId }
            )
          }
      }
    }
  }

  describe("filtering works by item LocationType") {
    def createItemWithLocationType(
      locationType: LocationType): Item[IdState.Minted] =
      createIdentifiedItemWith(
        locations = List(
          chooseFrom(
            createPhysicalLocationWith(locationType = locationType),
            createDigitalLocationWith(locationType = locationType)
          )
        )
      )

    val worksWithNoItem = indexedWorks(count = 3)

    val work1 = indexedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType("iiif-image"))
        ))
    val work2 = indexedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType("digit")),
          createItemWithLocationType(LocationType("dimgs"))
        ))
    val work3 = indexedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType("dpoaa"))
        ))

    val works = worksWithNoItem ++ Seq(work1, work2, work3)

    it("when listing works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = Seq(work1, work2)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.locationType=iiif-image,digit") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = matchingWorks.sortBy { _.state.canonicalId }
            )
          }
      }
    }

    it("when searching works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = Seq(work2)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=carrots&items.locations.locationType=digit") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = matchingWorks.sortBy { _.state.canonicalId }
            )
          }
      }
    }
  }

  describe("filtering works by Format") {
    val noFormatWorks = indexedWorks(count = 3)

    val bookWork = indexedWork()
      .title("apple")
      .format(Books)
    val cdRomWork = indexedWork()
      .title("apple")
      .format(CDRoms)
    val manuscriptWork = indexedWork()
      .title("apple")
      .format(ManuscriptsAsian)

    val works = noFormatWorks ++ Seq(bookWork, cdRomWork, manuscriptWork)

    it("when listing works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?workType=${ManuscriptsAsian.id}") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(manuscriptWork))
          }
      }
    }

    it("filters by multiple formats") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?workType=${ManuscriptsAsian.id},${CDRoms.id}") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(cdRomWork, manuscriptWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }

    it("when searching works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=apple&workType=${ManuscriptsAsian.id},${CDRoms.id}") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(cdRomWork, manuscriptWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }
  }

  describe("filtering works by type") {
    val collectionWork =
      indexedWork().title("rats").workType(WorkType.Collection)
    val seriesWork =
      indexedWork().title("rats").workType(WorkType.Series)
    val sectionWork =
      indexedWork().title("rats").workType(WorkType.Section)

    val works = Seq(collectionWork, seriesWork, sectionWork)

    it("when listing works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(routes, s"/$apiPrefix/works?type=Collection") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(collectionWork)
            )
          }
      }
    }

    it("filters by multiple types") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?type=Collection,Series",
            unordered = true) {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(collectionWork, seriesWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }

    it("when searching works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?query=rats&type=Series,Section",
            unordered = true) {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(seriesWork, sectionWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }
  }

  describe("filtering works by date range") {
    def createDatedWork(dateLabel: String): Work.Visible[WorkState.Indexed] =
      indexedWork()
        .production(
          List(createProductionEventWith(dateLabel = Some(dateLabel))))

    val work1709 = createDatedWork(dateLabel = "1709")
    val work1950 = createDatedWork(dateLabel = "1950")
    val work2000 = createDatedWork(dateLabel = "2000")

    it("filters by date range") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, work1709, work1950, work2000)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.from=1900-01-01&production.dates.to=1960-01-01") {
            Status.OK -> worksListResponse(apiPrefix, works = Seq(work1950))
          }
      }
    }

    it("filters by from date") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, work1709, work1950, work2000)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.from=1900-01-01") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(work1950, work2000).sortBy { _.state.canonicalId }
            )
          }
      }
    }

    it("filters by to date") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, work1709, work1950, work2000)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?production.dates.to=1960-01-01") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(work1709, work1950).sortBy { _.state.canonicalId }
            )
          }
      }
    }

    it("errors on invalid date") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, work1709, work1950, work2000)
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
    val english = Language(label = "English", id = "eng")
    val turkish = Language(label = "Turkish", id = "tur")

    val englishWork = indexedWork().languages(List(english))
    val turkishWork = indexedWork().languages(List(turkish))
    val noLanguageWork = indexedWork()

    val works = List(englishWork, turkishWork, noLanguageWork)

    it("filters by language") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?languages=eng") {
            Status.OK -> worksListResponse(apiPrefix, works = Seq(englishWork))
          }
      }
    }

    it("filters by multiple comma separated languages") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?languages=eng,tur") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(englishWork, turkishWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }
  }

  describe("filtering works by genre") {
    val horror = createGenreWith("horrible stuff")
    val romcom = createGenreWith("heartwarming stuff")

    val horrorWork = indexedWork().genres(List(horror))
    val romcomWork = indexedWork().genres(List(romcom))
    val romcomHorrorWork = indexedWork().genres(List(romcom, horror))
    val noGenreWork = indexedWork()

    val works = List(horrorWork, romcomWork, romcomHorrorWork, noGenreWork)

    it("filters by genre with partial match") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?genres.label=horrible") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(horrorWork, romcomHorrorWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }

    it("filters by genre using multiple terms") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
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

    val nineteenthCenturyWork =
      indexedWork().subjects(List(nineteenthCentury))
    val parisWork = indexedWork().subjects(List(paris))
    val nineteenthCenturyParisWork =
      indexedWork().subjects(List(nineteenthCentury, paris))
    val noSubjectWork = indexedWork()

    val works = List(
      nineteenthCenturyWork,
      parisWork,
      nineteenthCenturyParisWork,
      noSubjectWork)

    it("filters by subjects") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?subjects.label=paris") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(parisWork, nineteenthCenturyParisWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }

    it("filters by subjects using multiple terms") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
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
    def createLicensedWork(
      licenses: Seq[License]): Work.Visible[WorkState.Indexed] = {
      val items =
        licenses.map { license =>
          createDigitalItemWith(license = Some(license))
        }.toList

      indexedWork().items(items)
    }

    val ccByWork = createLicensedWork(licenses = List(License.CCBY))
    val ccByNcWork = createLicensedWork(licenses = List(License.CCBYNC))
    val bothLicenseWork =
      createLicensedWork(licenses = List(License.CCBY, License.CCBYNC))
    val noLicenseWork = createLicensedWork(licenses = List.empty)

    val works = List(ccByWork, ccByNcWork, bothLicenseWork, noLicenseWork)

    it("filters by license") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?license=cc-by") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(ccByWork, bothLicenseWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }

    it("filters by multiple licenses") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?license=cc-by,cc-by-nc") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(ccByWork, ccByNcWork, bothLicenseWork).sortBy {
                _.state.canonicalId
              }
            )
          }
      }
    }
  }

  describe("Identifiers filter") {
    val unknownWork = indexedWork()

    it("filters by a sourceIdentifier") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = indexedWork()
          insertIntoElasticsearch(worksIndex, unknownWork, work)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?identifiers=${work.sourceIdentifier.value}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work)
            )
          }
      }
    }

    it("filters by multiple sourceIdentifiers") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work1 = indexedWork()
          val work2 = indexedWork()

          insertIntoElasticsearch(worksIndex, unknownWork, work1, work2)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?identifiers=${work1.sourceIdentifier.value},${work2.sourceIdentifier.value}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work1, work2).sortBy(_.state.canonicalId)
            )
          }
      }
    }

    it("filters by an otherIdentifier") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work =
            indexedWork().otherIdentifiers(List(createSourceIdentifier))
          insertIntoElasticsearch(worksIndex, unknownWork, work)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?identifiers=${work.data.otherIdentifiers.head.value}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work)
            )
          }
      }
    }

    it("filters by multiple otherIdentifiers") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work1 =
            indexedWork().otherIdentifiers(List(createSourceIdentifier))
          val work2 =
            indexedWork().otherIdentifiers(List(createSourceIdentifier))

          insertIntoElasticsearch(worksIndex, unknownWork, work1, work2)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?identifiers=${work1.data.otherIdentifiers.head.value},${work2.data.otherIdentifiers.head.value}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work1, work2).sortBy(_.state.canonicalId)
            )
          }
      }
    }

    it("filters by mixed identifiers") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work1 = indexedWork()
          val work2 =
            indexedWork().otherIdentifiers(List(createSourceIdentifier))

          insertIntoElasticsearch(worksIndex, unknownWork, work1, work2)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?identifiers=${work1.sourceIdentifier.value},${work2.data.otherIdentifiers.head.value}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(work1, work2).sortBy(_.state.canonicalId)
            )
          }
      }
    }
  }

  describe("Access status filter") {
    def work(status: AccessStatus): Work.Visible[WorkState.Indexed] =
      indexedWork()
        .items(
          List(
            createIdentifiedItemWith(
              locations = List(
                createDigitalLocationWith(
                  accessConditions = List(
                    AccessCondition(
                      status = Some(status)
                    )
                  )
                )
              )
            )
          )
        )

    val workA = work(AccessStatus.Restricted)
    val workB = work(AccessStatus.Restricted)
    val workC = work(AccessStatus.Closed)
    val workD = work(AccessStatus.Open)
    val workE = work(AccessStatus.OpenWithAdvisory)

    it("includes works by access status") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, workA, workB, workC, workD, workE)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.accessConditions.status=restricted,closed") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(workA, workB, workC).sortBy(_.state.canonicalId)
            )
          }
      }
    }

    it("excludes works by access status") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, workA, workB, workC, workD, workE)
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.accessConditions.status=!restricted,!closed") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(workD, workE).sortBy(_.state.canonicalId)
            )
          }
      }
    }
  }
}
