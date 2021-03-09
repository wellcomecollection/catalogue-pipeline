package uk.ac.wellcome.platform.api.works

import com.sksamuel.elastic4s.Index
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
import WorkState.Indexed
import org.scalatest.prop.TableDrivenPropertyChecks

import java.net.URLEncoder

class WorksFiltersTest
    extends ApiWorksTestBase
    with ItemsGenerators
    with ProductionEventGenerators
    with TableDrivenPropertyChecks {

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

  describe("filtering works by item LocationType") {
    def createItemWithLocationType(
      locationType: LocationType): Item[IdState.Minted] =
      createIdentifiedItemWith(
        locations = List(
          locationType match {
            case LocationType.ClosedStores =>
              createPhysicalLocationWith(
                locationType = LocationType.ClosedStores,
                label = LocationType.ClosedStores.label
              )

            case physicalLocationType: PhysicalLocationType =>
              createPhysicalLocationWith(locationType = physicalLocationType)

            case digitalLocationType: DigitalLocationType =>
              createDigitalLocationWith(locationType = digitalLocationType)
          }
        )
      )

    val worksWithNoItem = indexedWorks(count = 3)

    val work1 = indexedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType.IIIFImageAPI)
        ))
    val work2 = indexedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType.IIIFImageAPI),
          createItemWithLocationType(LocationType.IIIFPresentationAPI)
        ))
    val work3 = indexedWork()
      .title("Crumbling carrots")
      .items(
        List(
          createItemWithLocationType(LocationType.ClosedStores)
        ))

    val works = worksWithNoItem ++ Seq(work1, work2, work3)

    it("when listing works") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(worksIndex, works: _*)

          val matchingWorks = Seq(work1, work2)

          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?items.locations.locationType=iiif-image,iiif-presentation") {
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
            s"/$apiPrefix/works?query=carrots&items.locations.locationType=iiif-presentation") {
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
    val annualReports = createGenreWith("Annual reports.")
    val pamphlets = createGenreWith("Pamphlets.")
    val psychology = createGenreWith("Psychology, Pathological")
    val darwin = createGenreWith("Darwin \"Jones\", Charles")

    val annualReportsWork = indexedWork().genres(List(annualReports))
    val pamphletsWork = indexedWork().genres(List(pamphlets))
    val psychologyWork = indexedWork().genres(List(psychology))
    val darwinWork =
      indexedWork().genres(List(darwin))
    val mostThingsWork =
      indexedWork().genres(List(pamphlets, psychology, darwin))
    val nothingWork = indexedWork()

    val works =
      List(
        annualReportsWork,
        pamphletsWork,
        psychologyWork,
        darwinWork,
        mostThingsWork,
        nothingWork)

    val testCases = Table(
      ("query", "results", "clue"),
      ("Annual reports.", Seq(annualReportsWork), "single match single genre"),
      (
        "Pamphlets.",
        Seq(pamphletsWork, mostThingsWork),
        "multi match single genre"),
      (
        "Annual reports.,Pamphlets.",
        Seq(annualReportsWork, pamphletsWork, mostThingsWork),
        "comma separated"),
      (
        """Annual reports.,"Psychology, Pathological"""",
        Seq(annualReportsWork, psychologyWork, mostThingsWork),
        "commas in quotes"),
      (
        """"Darwin \"Jones\", Charles","Psychology, Pathological",Pamphlets.""",
        Seq(darwinWork, psychologyWork, mostThingsWork, pamphletsWork),
        "escaped quotes in quotes")
    )

    it("filters by genres as a comma separated list") {
      forAll(testCases) {
        (query: String,
         results: Seq[Work.Visible[WorkState.Indexed]],
         clue: String) =>
          withClue(clue) {
            withWorksApi {
              case (worksIndex, routes) =>
                insertIntoElasticsearch(worksIndex, works: _*)
                assertJsonResponse(
                  routes,
                  s"/$apiPrefix/works?genres.label=${URLEncoder.encode(query, "UTF-8")}") {
                  Status.OK -> worksListResponse(
                    apiPrefix,
                    works = results.sortBy {
                      _.state.canonicalId
                    }
                  )
                }
            }
          }
      }
    }
  }

  describe("filtering works by subject") {
    val sanitation = createSubjectWith("Sanitation.")
    val london = createSubjectWith("London (England)")
    val psychology = createSubjectWith("Psychology, Pathological")
    val darwin = createSubjectWith("Darwin \"Jones\", Charles")

    val sanitationWork = indexedWork().subjects(List(sanitation))
    val londonWork = indexedWork().subjects(List(london))
    val psychologyWork = indexedWork().subjects(List(psychology))
    val darwinWork =
      indexedWork().subjects(List(darwin))
    val mostThingsWork =
      indexedWork().subjects(List(london, psychology, darwin))
    val nothingWork = indexedWork()

    val works =
      List(
        sanitationWork,
        londonWork,
        psychologyWork,
        darwinWork,
        mostThingsWork,
        nothingWork)

    val testCases = Table(
      ("query", "results", "clue"),
      ("Sanitation.", Seq(sanitationWork), "single match single subject"),
      (
        "London (England)",
        Seq(londonWork, mostThingsWork),
        "multi match single subject"),
      (
        "Sanitation.,London (England)",
        Seq(sanitationWork, londonWork, mostThingsWork),
        "comma separated"),
      (
        """Sanitation.,"Psychology, Pathological"""",
        Seq(sanitationWork, psychologyWork, mostThingsWork),
        "commas in quotes"),
      (
        """"Darwin \"Jones\", Charles","Psychology, Pathological",London (England)""",
        Seq(darwinWork, psychologyWork, londonWork, mostThingsWork),
        "escaped quotes in quotes")
    )

    it("filters by subjects as a comma separated list") {
      forAll(testCases) {
        (query: String,
         results: Seq[Work.Visible[WorkState.Indexed]],
         clue: String) =>
          withClue(clue) {
            withWorksApi {
              case (worksIndex, routes) =>
                insertIntoElasticsearch(worksIndex, works: _*)
                assertJsonResponse(
                  routes,
                  s"/$apiPrefix/works?subjects.label=${URLEncoder.encode(query, "UTF-8")}") {
                  Status.OK -> worksListResponse(
                    apiPrefix,
                    works = results.sortBy {
                      _.state.canonicalId
                    }
                  )
                }
            }
          }
      }
    }
  }

  describe("filtering works by contributors") {
    val patricia = Contributor(agent = Person("Bath, Patricia"), roles = Nil)
    val karlMarx = Contributor(agent = Person("Karl Marx"), roles = Nil)
    val jakePaul = Contributor(agent = Person("Jake Paul"), roles = Nil)
    val darwin =
      Contributor(agent = Person("Darwin \"Jones\", Charles"), roles = Nil)

    val patriciaWork = indexedWork().contributors(List(patricia))
    val karlMarxWork =
      indexedWork().contributors(List(karlMarx))
    val jakePaulWork =
      indexedWork().contributors(List(jakePaul))
    val darwinWork = indexedWork().contributors(List(darwin))
    val patriciaDarwinWork = indexedWork()
      .contributors(List(patricia, darwin))
    val noContributorsWork = indexedWork().contributors(Nil)

    val works = List(
      patriciaWork,
      karlMarxWork,
      jakePaulWork,
      darwinWork,
      patriciaDarwinWork,
      noContributorsWork)

    val testCases = Table(
      ("query", "results", "clue"),
      ("Karl Marx", Seq(karlMarxWork), "single match"),
      (
        """"Bath, Patricia"""",
        Seq(patriciaWork, patriciaDarwinWork),
        "multi match"),
      (
        "Karl Marx,Jake Paul",
        Seq(karlMarxWork, jakePaulWork),
        "comma separated"),
      (
        """"Bath, Patricia",Karl Marx""",
        Seq(patriciaWork, patriciaDarwinWork, karlMarxWork),
        "commas in quotes"),
      (
        """"Bath, Patricia",Karl Marx,"Darwin \"Jones\", Charles"""",
        Seq(patriciaWork, karlMarxWork, darwinWork, patriciaDarwinWork),
        "quotes in quotes"),
    )

    it("filters by contributors as a comma separated list") {
      forAll(testCases) {
        (query: String,
         results: Seq[Work.Visible[WorkState.Indexed]],
         clue: String) =>
          withClue(clue) {
            withWorksApi {
              case (worksIndex, routes) =>
                insertIntoElasticsearch(worksIndex, works: _*)
                assertJsonResponse(
                  routes,
                  s"/$apiPrefix/works?contributors.agent.label=${URLEncoder
                    .encode(query, "UTF-8")}") {
                  Status.OK -> worksListResponse(
                    apiPrefix,
                    works = results.sortBy {
                      _.state.canonicalId
                    }
                  )
                }
            }
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
    def work(status: AccessStatus): Work.Visible[Indexed] =
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

  describe("availabilities filter") {
    val onlineWork = indexedWork().items(
      List(createDigitalItemWith(accessStatus = AccessStatus.Open)))
    val inLibraryWork = indexedWork().items(List(createIdentifiedPhysicalItem))
    val onlineAndInLibraryWork = indexedWork().items(
      List(
        createDigitalItemWith(accessStatus = AccessStatus.OpenWithAdvisory),
        createIdentifiedPhysicalItem))

    it("filters by availability ID") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(
            worksIndex,
            onlineWork,
            inLibraryWork,
            onlineAndInLibraryWork)
          assertJsonResponse(
            routes = routes,
            unordered = true,
            path = s"/$apiPrefix/works?availabilities=online") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(onlineWork, onlineAndInLibraryWork)
            )
          }
      }
    }

    it("filters by multiple comma-separated availability IDs") {
      withWorksApi {
        case (worksIndex, routes) =>
          insertIntoElasticsearch(
            worksIndex,
            onlineWork,
            inLibraryWork,
            onlineAndInLibraryWork)
          assertJsonResponse(
            routes = routes,
            unordered = true,
            path = s"/$apiPrefix/works?availabilities=in-library,online") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(onlineWork, inLibraryWork, onlineAndInLibraryWork)
            )
          }
      }
    }
  }

  describe("relation filters") {

    def work(path: String): Work.Visible[Indexed] =
      indexedWork(sourceIdentifier = createSourceIdentifierWith(value = path))
        .collectionPath(CollectionPath(path = path))
        .title(path)

    val workA = work("A")
    val workB = work("A/B").ancestors(workA)
    val workC = work("A/C").ancestors(workA)
    val workD = work("A/C/D").ancestors(workA, workC)
    val workE = work("A/C/D/E").ancestors(workA, workC, workD)
    val workX = work("X")

    def storeWorks(index: Index) =
      insertIntoElasticsearch(index, workA, workB, workC, workD, workE, workX)

    it("filters partOf from root position") {
      withWorksApi {
        case (worksIndex, routes) =>
          storeWorks(worksIndex)
          assertJsonResponse(routes, s"/$apiPrefix/works?partOf=${workA.id}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works =
                Seq(workB, workC, workD, workE).sortBy(_.state.canonicalId)
            )
          }
      }
    }

    it("filters partOf from non root position") {
      withWorksApi {
        case (worksIndex, routes) =>
          storeWorks(worksIndex)
          assertJsonResponse(routes, s"/$apiPrefix/works?partOf=${workC.id}") {
            Status.OK -> worksListResponse(
              apiPrefix = apiPrefix,
              works = Seq(workD, workE).sortBy(_.state.canonicalId)
            )
          }
      }
    }
  }
}
