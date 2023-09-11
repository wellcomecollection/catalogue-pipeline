package weco.pipeline.ingestor.works

import io.circe.Json
import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocationType,
  License,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.internal_model.work.Format.{
  Audio,
  Books,
  Journals,
  Pictures
}
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators._
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.TestDocumentUtils

import java.time.Instant

/** Creates the example documents we use in the API tests.
  *
  * These tests use a seeded RNG to ensure deterministic results; to prevent
  * regenerating existing examples and causing unnecessary churn in the API
  * tests when values change, I suggest adding new examples at the bottom of
  * this file.
  *
  * Also, be careful removing or editing existing examples. It may be easier to
  * add a new example than remove an old one, to prevent regenerating some of
  * the examples you aren't editing.
  *
  * Note: for some reason you get slightly different results if you run this
  * test in IntelliJ vs through Docker. CI will use Docker and check you've
  * committed the latest versions, so run it in Docker when you want to rebuild.
  */
class CreateTestWorkDocuments
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ItemsGenerators
    with PeriodGenerators
    with TestDocumentUtils
    with SubjectGenerators
    with GenreGenerators
    with ContributorGenerators
    with ProductionEventGenerators
    with LanguageGenerators
    with ImageGenerators
    with HoldingsGenerators {
  override def randomInstant: Instant =
    Instant.parse("2001-01-01T01:01:01Z").plusSeconds(random.nextInt())

  it("creates works of different types") {
    saveWorks(
      works = (1 to 5).map(_ => denormalisedWork()).sortBy(_.state.canonicalId),
      description = "an arbitrary list of visible works",
      id = "works.visible"
    )
    saveWorks(
      works = (1 to 3).map(_ => denormalisedWork().invisible()),
      description = "an arbitrary list of invisible works",
      id = "works.invisible"
    )
    saveWorks(
      works = (1 to 2).map(
        _ =>
          denormalisedWork().redirected(
            redirectTarget = IdState.Identified(
              canonicalId = createCanonicalId,
              sourceIdentifier = createSourceIdentifier
            )
          )
      ),
      description = "an arbitrary list of redirected works",
      id = "works.redirected"
    )
    saveWorks(
      works = (1 to 4).map(_ => denormalisedWork().deleted()),
      description = "an arbitrary list of deleted works",
      id = "works.deleted"
    )
  }

  it("creates works with optional fields") {
    saveWork(
      work = denormalisedWork()
        .edition("Special edition")
        .duration(3600),
      description = "a work with optional top-level fields",
      id = "work-with-edition-and-duration"
    )

    saveWork(
      work = denormalisedWork().thumbnail(createDigitalLocation),
      description = "a work with a thumbnail",
      id = "work-thumbnail"
    )
  }

  it("creates works with specific values that can be searched on") {
    saveWork(
      work = denormalisedWork()
        .title("A drawing of a dodo")
        .lettering("A line of legible ligatures"),
      description = "a work with 'dodo' in the title",
      id = "work-title-dodo"
    )
    saveWork(
      work = denormalisedWork()
        .title("A mezzotint of a mouse")
        .lettering("A print of proportional penmanship"),
      description = "a work with 'mouse' in the title",
      id = "work-title-mouse"
    )
  }

  it("creates works with specific production events") {
    Seq("1900", "1976", "1904", "2020", "1098").foreach {
      year =>
        saveWork(
          work = denormalisedWork()
            .production(
              List(
                ProductionEvent(
                  label = randomAlphanumeric(25),
                  places = List(),
                  agents = List(),
                  dates = List(createPeriodForYear(year))
                )
              )
            )
            .title(s"Production event in $year"),
          description = s"a work with a production event in $year",
          id = s"work-production.$year"
        )
    }

    saveWork(
      work = denormalisedWork()
        .title("An invisible mezzotint of a mouse")
        .invisible(),
      description = "an invisible work with 'mouse' in the title",
      id = "work.invisible.title-mouse"
    )
  }

  it("creates works that populate all the include-able fields") {
    saveWorks(
      works = (1 to 3).map(
        _ =>
          denormalisedWork(
            relations = Relations(
              ancestors = List(
                Relation(mergedWork(), 0, 1, 5),
                Relation(mergedWork(), 1, 3, 4)
              ),
              children = List(Relation(mergedWork(), 3, 0, 0)),
              siblingsPreceding = List(Relation(mergedWork(), 2, 0, 0)),
              siblingsSucceeding = List(Relation(mergedWork(), 2, 0, 0))
            )
          ).title("A work with all the include-able fields")
            .otherIdentifiers(List(createSourceIdentifier))
            .subjects((1 to 2).map(_ => createSubject).toList)
            .genres((1 to 2).map(_ => createGenre).toList)
            .contributors(
              (1 to 2)
                .map(
                  _ =>
                    createPersonContributorWith(label =
                      s"person-${randomAlphanumeric()}"
                    )
                )
                .toList
            )
            .production((1 to 2).map(_ => createProductionEvent).toList)
            .languages((1 to 3).map(_ => createLanguage).toList)
            .notes(
              (1 to 4)
                .map(
                  _ =>
                    Note(
                      contents = randomAlphanumeric(),
                      noteType = chooseFrom(
                        NoteType.GeneralNote,
                        NoteType.FundingInformation,
                        NoteType.LocationOfDuplicatesNote
                      )
                    )
                )
                .toList
            )
            .imageData((1 to 2).map(_ => createImageData.toIdentified).toList)
            .holdings(createHoldings(3))
            .formerFrequency(List("Published in 2001", "Published in 2002"))
            .designation(
              List("Designation #1", "Designation #2", "Designation #3")
            )
            .items(
              (1 to 2)
                .map(_ => createIdentifiedItem)
                .toList :+ createUnidentifiableItem
            )
      ),
      description = "a list of work with all the include-able fields",
      id = "work.visible.everything"
    )

    // Create some examples to use in the format filter and aggregation tests
    val formats = (1 to 4).map(_ => Format.Books) ++ (1 to 3).map(
      _ => Format.Journals
    ) ++ (1 to 2).map(_ => Format.Audio) :+ Format.Pictures
    formats.zipWithIndex.foreach {
      case (format, i) =>
        saveWork(
          work = denormalisedWork()
            .format(format)
            .title(s"A work with format $format"),
          description = "one of a list of works with a variety of formats",
          id = s"works.formats.$i.$format"
        )
    }

    saveWork(
      work = denormalisedWork().title(
        "+a -title | with (all the simple) query~4 syntax operators in it*"
      ),
      description = "a work whose title has lots of ES query syntax operators",
      id = "works.title-query-syntax"
    )
    saveWork(
      work = denormalisedWork().title("(a b c d e) h"),
      description = "a work whose title has parens meant to trip up ES",
      id = "works.title-query-parens"
    )

    // Create some examples to use in the language filter and aggregation tests
    val english = Language(label = "English", id = "eng")
    val swedish = Language(label = "Swedish", id = "swe")
    val turkish = Language(label = "Turkish", id = "tur")

    val languageCombos = Seq(
      List(english),
      List(english),
      List(english),
      List(english, swedish),
      List(english, swedish, turkish),
      List(swedish),
      List(turkish)
    )
    languageCombos.zipWithIndex.foreach {
      case (languages, i) =>
        val label = languages.map(_.label).mkString(", ")
        val id = languages.map(_.id).mkString("+")

        saveWork(
          work = denormalisedWork()
            .languages(languages)
            .title(s"A work with languages $label"),
          description = "one of a list of works with a variety of languages",
          id = s"works.languages.$i.$id"
        )
    }
  }

  it("creates examples to use in the license format/aggregation tests") {
    val licenseCombos = List(
      List(License.CCBY),
      List(License.CCBY),
      List(License.CCBYNC),
      List(License.CCBY, License.CCBYNC),
      List.empty
    )

    val itemCombos = licenseCombos.map {
      licenses =>
        licenses.map(lic => createDigitalItemWith(license = Some(lic)))
    }

    val works = itemCombos.map(items => denormalisedWork().items(items))

    saveWorks(
      works = works,
      description = "a work with licensed digital items",
      id = "works.items-with-licenses"
    )
  }

  it("creates an example to use in the genre format/aggregation tests") {
    val concept0 = Concept("Conceptual Conversations")
    val concept1 = Place("Pleasant Paris")
    val concept2 = Period(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Period"
        )
      ),
      label = "Past Prehistory",
      range = None
    )

    val genre = Genre(
      label = "Electronic books",
      concepts = List(concept0, concept1, concept2)
    )

    saveWork(
      work = denormalisedWork()
        .title("A work with different concepts in the genre")
        .genres(List(genre)),
      description = "a work with different concepts in the genre",
      id = "works.genres"
    )
  }

  it("creates examples to use in the subject filter/aggregation tests") {
    val paleoNeuroBiology = createSubjectWith(label = "paleoNeuroBiology")
    val realAnalysis = createSubjectWith(label = "realAnalysis")

    val subjectLists = List(
      List(paleoNeuroBiology),
      List(realAnalysis),
      List(realAnalysis),
      List(paleoNeuroBiology, realAnalysis),
      List.empty
    )

    val works = subjectLists
      .map { denormalisedWork().subjects(_) }

    saveWorks(
      works = works,
      description = "works with different subjects",
      id = "works.subjects"
    )
  }

  it("creates examples to use in the contributor filter/aggregation tests") {
    val agent47 = Agent("47")
    val jamesBond = Agent("007")
    val mi5 = Organisation("MI5")
    val gchq = Organisation("GCHQ")

    val agentCombos = List(
      List(agent47),
      List(agent47),
      List(jamesBond, mi5),
      List(mi5, gchq)
    )

    val works = agentCombos.map {
      agents =>
        denormalisedWork().contributors(agents.map(Contributor(_, roles = Nil)))
    }

    saveWorks(
      works = works,
      description = "works with different contributor",
      id = "works.contributor"
    )
  }

  it("creates items with multiple source identifiers") {
    val works = (1 to 5).map {
      _ =>
        denormalisedWork()
          .items(
            List(
              createIdentifiedItemWith(
                otherIdentifiers = List(createSourceIdentifier)
              )
            )
          )
    }

    saveWorks(
      works = works,
      description = "works with items with other identifiers",
      id = "works.items-with-other-identifiers"
    )
  }

  it("creates a work with a collection path") {
    saveWork(
      work = denormalisedWork()
        .collectionPath(CollectionPath(path = "PPCRI", label = Some("PP/CRI"))),
      description = "a work with a collection path",
      id = "works.collection-path.PPCRI"
    )

    saveWork(
      work = denormalisedWork()
        .collectionPath(CollectionPath("NUFFINK", label = Some("NUF/FINK"))),
      description = "a work with a collection path",
      id = "works.collection-path.NUFFINK"
    )
  }

  it("creates examples of every format") {
    val works = Format.values.map {
      format =>
        denormalisedWork().format(format)
    }

    saveWorks(
      works = works,
      description = "works with every format",
      id = "works.every-format"
    )
  }

  it("creates examples to use in the period filter/aggregation tests") {
    val periods = List(
      createPeriodForYear(year = "1850"),
      createPeriodForYearRange(startYear = "1850", endYear = "2000"),
      createPeriodForYearRange(startYear = "1860", endYear = "1960"),
      createPeriodForYear(year = "1960"),
      createPeriodForYearRange(startYear = "1960", endYear = "1964"),
      createPeriodForYear(year = "1962")
    )

    val works = periods.map {
      p =>
        denormalisedWork()
          .production(
            List(createProductionEvent.copy(dates = List(p)))
          )
    }

    saveWorks(
      works = works,
      description = "works with multi-year production ranges",
      id = "works.production.multi-year"
    )
  }

  it("creates items with different location types") {
    val locationTypes = Seq(
      List(LocationType.IIIFImageAPI),
      List(LocationType.IIIFImageAPI, LocationType.IIIFPresentationAPI),
      List(LocationType.ClosedStores)
    )

    val locations = locationTypes.map {
      _.map {
        case physicalLocationType: PhysicalLocationType =>
          createPhysicalLocationWith(locationType = physicalLocationType)

        case digitalLocationType: DigitalLocationType =>
          createDigitalLocationWith(locationType = digitalLocationType)
      }
    }

    val items = locations.map {
      locations =>
        createIdentifiedItemWith(locations = locations)
    }

    val works = items.map {
      item =>
        denormalisedWork().items(List(item))
    }

    saveWorks(
      works,
      description = "items with different location types",
      id = "work.items-with-location-types"
    )
  }

  it("creates examples for the aggregation-with-filters tests") {
    val formats = Format.values
    val subjects = (0 to 5).map(_ => createSubject)
    val works = formats.zipWithIndex.map {
      case (format, i) =>
        denormalisedWork()
          .format(format)
          .subjects(List(subjects(i / subjects.size)))
    }

    saveWorks(
      works,
      description = "examples for the aggregation-with-filters tests",
      id = "works.examples.aggregation-with-filters-tests"
    )
  }

  it("creates examples for works of different types") {
    Seq(
      WorkType.Section,
      WorkType.Collection,
      WorkType.Series
    ).map {
      workType =>
        saveWork(
          work = denormalisedWork().title("rats").workType(workType),
          description = "examples of works with different types",
          id = s"works.examples.different-work-types.$workType"
        )
    }
  }

  it("creates examples for the genre filter tests") {
    val annualReports = createGenreWith(
      "Annual reports",
      concepts = List(
        createGenreConcept(canonicalId = "g00dcafe"),
        createConcept("baadf00d")
      )
    )
    val pamphlets = createGenreWith(
      label = "Pamphlets",
      concepts = List(createGenreConcept(canonicalId = "g00dcafe"))
    )
    val psychology = createGenreWith(
      label = "Psychology, Pathological",
      concepts = List(createGenreConcept(canonicalId = "baadf00d"))
    )
    val darwin = createGenreWith("Darwin \"Jones\", Charles")

    val annualReportsWork = denormalisedWork().genres(List(annualReports))
    val pamphletsWork = denormalisedWork().genres(List(pamphlets))
    val psychologyWork = denormalisedWork().genres(List(psychology))
    val darwinWork =
      denormalisedWork().genres(List(darwin))
    val mostThingsWork =
      denormalisedWork().genres(List(pamphlets, psychology, darwin))
    val nothingWork = denormalisedWork()

    val works =
      List(
        annualReportsWork,
        pamphletsWork,
        psychologyWork,
        darwinWork,
        mostThingsWork,
        nothingWork
      )

    saveWorks(
      works,
      description = "examples for the genre tests",
      id = s"works.examples.genre-filters-tests"
    )
  }

  it("creates examples for the subject filter tests") {
    val sanitation =
      Subject(
        id = IdState.Identified(
          canonicalId = CanonicalId("sanitati"),
          sourceIdentifier = SourceIdentifier(
            identifierType = IdentifierType.LCSubjects,
            value = "lcsh-sanitation",
            ontologyType = "Subject"
          ),
          otherIdentifiers = List(
            SourceIdentifier(
              identifierType = IdentifierType.MESH,
              value = "mesh-sanitation",
              ontologyType = "Subject"
            )
          )
        ),
        label = "Sanitation.",
        concepts = createConcepts()
      )

    val london = createSubjectWith("London (England)")
    val psychology = createSubjectWith("Psychology, Pathological")

    val darwin =
      Subject(
        id = IdState.Identified(
          canonicalId = CanonicalId("darwin01"),
          sourceIdentifier = SourceIdentifier(
            identifierType = IdentifierType.LCNames,
            value = "lcnames-darwin",
            ontologyType = "Subject"
          )
        ),
        label = "Darwin \"Jones\", Charles",
        concepts = createConcepts()
      )

    val sanitationWork = denormalisedWork().subjects(List(sanitation))
    val londonWork = denormalisedWork().subjects(List(london))
    val psychologyWork = denormalisedWork().subjects(List(psychology))
    val darwinWork =
      denormalisedWork().subjects(List(darwin))
    val mostThingsWork =
      denormalisedWork().subjects(List(london, psychology, darwin))
    val nothingWork = denormalisedWork()

    val works =
      List(
        sanitationWork,
        londonWork,
        psychologyWork,
        darwinWork,
        mostThingsWork,
        nothingWork
      )

    saveWorks(
      works,
      description = "examples for the subject filter tests",
      id = s"works.examples.subject-filters-tests"
    )
  }

  it("creates examples for the contributor filter tests") {
    val patricia = Contributor(agent = Person("Bath, Patricia"), roles = Nil)
    val karlMarx = Contributor(agent = Person("Karl Marx"), roles = Nil)
    val jakePaul = Contributor(agent = Person("Jake Paul"), roles = Nil)
    val darwin =
      Contributor(agent = Person("Darwin \"Jones\", Charles"), roles = Nil)

    val patriciaWork = denormalisedWork().contributors(List(patricia))
    val karlMarxWork =
      denormalisedWork().contributors(List(karlMarx))
    val jakePaulWork =
      denormalisedWork().contributors(List(jakePaul))
    val darwinWork = denormalisedWork().contributors(List(darwin))
    val patriciaDarwinWork = denormalisedWork()
      .contributors(List(patricia, darwin))
    val noContributorsWork = denormalisedWork().contributors(Nil)

    val works = List(
      patriciaWork,
      karlMarxWork,
      jakePaulWork,
      darwinWork,
      patriciaDarwinWork,
      noContributorsWork
    )

    saveWorks(
      works,
      description = "examples for the contributor filter tests",
      id = s"works.examples.contributor-filters-tests"
    )
  }

  it("creates examples for the access status filter tests") {
    def work(status: AccessStatus): Work.Visible[WorkState.Denormalised] =
      denormalisedWork()
        .items(
          List(
            createIdentifiedItemWith(
              locations = List(
                createDigitalLocationWith(
                  accessConditions = List(
                    AccessCondition(
                      method = AccessMethod.ManualRequest,
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
    val workF = work(
      AccessStatus.LicensedResources(relationship = LicensedResources.Resource)
    )
    val workG = work(
      AccessStatus
        .LicensedResources(relationship = LicensedResources.RelatedResource)
    )

    val works = Seq(workA, workB, workC, workD, workE, workF, workG)

    saveWorks(
      works,
      description = "examples for the access status tests",
      id = s"works.examples.access-status-filters-tests"
    )
  }

  it("creates examples for the works with filtered aggregations tests") {
    val bashkir = Language(label = "Bashkir", id = "bak")
    val marathi = Language(label = "Marathi", id = "mar")
    val quechua = Language(label = "Quechua", id = "que")
    val chechen = Language(label = "Chechen", id = "che")

    /*
     * | workType     | count |
     * |--------------|-------|
     * | a / Books    | 4     |
     * | d / Journals | 3     |
     * | i / Audio    | 2     |
     * | k / Pictures | 1     |
     *
     * | language      | count |
     * |---------------|-------|
     * | bak / Bashkir | 4     |
     * | que / Quechua | 3     |
     * | mar / Marathi  | 2     |
     * | che / Chechen | 1     |
     *
     */
    val aggregatedWorks: List[Work.Visible[WorkState.Denormalised]] = List(
      (Books, bashkir, "rats"), // a
      (Journals, marathi, "capybara"), // d
      (Pictures, quechua, "tapirs"), // k
      (Audio, bashkir, "rats"), // i
      (Books, bashkir, "capybara"), // a
      (Books, bashkir, "tapirs"), // a
      (Journals, quechua, "rats"), // d
      (Books, marathi, "capybara"), // a
      (Journals, quechua, "tapirs"), // d
      (Audio, chechen, "rats") // i
    ).map {
      case (format, language, title) =>
        denormalisedWork()
          .title(title)
          .format(format)
          .languages(List(language))
    }

    saveWorks(
      aggregatedWorks,
      description = "examples for the works filtered aggregations tests",
      id = s"works.examples.filtered-aggregations-tests"
    )
  }

  private def saveWork(
    work: Work[WorkState.Denormalised],
    description: String,
    id: String
  ): Unit =
    saveWorks(works = List(work), description, id)

  private def saveWorks(
    works: Seq[Work[WorkState.Denormalised]],
    description: String,
    id: String
  ): Unit = {
    val documents = works match {
      case Seq(work) =>
        Seq(
          id -> TestDocument(
            description,
            id = work.id,
            document = work.toDocument
          )
        )

      case _ =>
        works.zipWithIndex
          .map {
            case (work, index) =>
              s"$id.$index" -> TestDocument(
                description,
                id = work.id,
                document = work.toDocument
              )
          }
    }

    saveDocuments(documents)
  }

  implicit class WorkOps(work: Work[WorkState.Denormalised]) {
    def toDocument: Json = {
      // This is a fixed date so we get consistent values in the indexedTime
      // field in the generated documents.
      val transformer = new WorkTransformer {
        override protected def getIndexedTime: Instant =
          Instant.parse("2001-01-01T01:01:01.00Z")
      }

      transformer.deriveData(work).asJson
    }
  }
}
