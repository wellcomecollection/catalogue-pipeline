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
    val work1 = identifiedWork()
      .genres(List(createGenreWith(label = "horror")))
      .subjects(List(createSubjectWith(label = "france")))
    val work2 = identifiedWork()
      .genres(List(createGenreWith(label = "horror")))
      .subjects(List(createSubjectWith(label = "england")))
    val work3 = identifiedWork()
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
      identifiedWork()
        .title("An example digital work")
        .items(
          List(
            createIdentifiedItemWith(locations = List(createDigitalLocation)))
        )
    }

    val physicalWorks = (1 to 2).map { _ =>
      identifiedWork()
        .title("An example physical work")
        .items(
          List(
            createIdentifiedItemWith(locations = List(createPhysicalLocation)))
        )
    }

    val comboWorks = (1 to 2).map { _ =>
      identifiedWork()
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

    val worksWithNoItem = identifiedWorks(count = 3)

    val work1 = identifiedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType("iiif-image"))
        ))
    val work2 = identifiedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType("digit")),
          createItemWithLocationType(LocationType("dimgs"))
        ))
    val work3 = identifiedWork()
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
    val noFormatWorks = identifiedWorks(count = 3)

    val bookWork = identifiedWork()
      .title("apple")
      .format(Books)
    val cdRomWork = identifiedWork()
      .title("apple")
      .format(CDRoms)
    val manuscriptWork = identifiedWork()
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
      identifiedWork().title("rats").workType(WorkType.Collection)
    val seriesWork =
      identifiedWork().title("rats").workType(WorkType.Series)
    val sectionWork =
      identifiedWork().title("rats").workType(WorkType.Section)

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
    def createDatedWork(dateLabel: String): Work.Visible[WorkState.Identified] =
      identifiedWork()
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
    val englishWork =
      identifiedWork().language(Language(label = "English", id = "eng"))
    val germanWork =
      identifiedWork().language(Language(label = "German", id = "ger"))
    val noLanguageWork = identifiedWork()

    val works = List(englishWork, germanWork, noLanguageWork)

    it("filters by language") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?language=eng") {
            Status.OK -> worksListResponse(apiPrefix, works = Seq(englishWork))
          }
      }
    }

    it("filters by multiple comma seperated languages") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)
          assertJsonResponse(routes, s"/$apiPrefix/works?language=eng,ger") {
            Status.OK -> worksListResponse(
              apiPrefix,
              works = Seq(englishWork, germanWork).sortBy {
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

    val horrorWork = identifiedWork().genres(List(horror))
    val romcomWork = identifiedWork().genres(List(romcom))
    val romcomHorrorWork = identifiedWork().genres(List(romcom, horror))
    val noGenreWork = identifiedWork()

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
      identifiedWork().subjects(List(nineteenthCentury))
    val parisWork = identifiedWork().subjects(List(paris))
    val nineteenthCenturyParisWork =
      identifiedWork().subjects(List(nineteenthCentury, paris))
    val noSubjectWork = identifiedWork()

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
      licenses: Seq[License]): Work.Visible[WorkState.Identified] = {
      val items =
        licenses.map { license =>
          createDigitalItemWith(license = Some(license))
        }.toList

      identifiedWork().items(items)
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
    val unknownWork = identifiedWork()

    it("filters by a sourceIdentifier") {
      withWorksApi {
        case (worksIndex, routes) =>
          val work = identifiedWork()
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
          val work1 = identifiedWork()
          val work2 = identifiedWork()

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
            identifiedWork().otherIdentifiers(List(createSourceIdentifier))
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
            identifiedWork().otherIdentifiers(List(createSourceIdentifier))
          val work2 =
            identifiedWork().otherIdentifiers(List(createSourceIdentifier))

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
          val work1 = identifiedWork()
          val work2 =
            identifiedWork().otherIdentifiers(List(createSourceIdentifier))

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
    def work(status: AccessStatus): Work.Visible[WorkState.Identified] =
      identifiedWork()
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
