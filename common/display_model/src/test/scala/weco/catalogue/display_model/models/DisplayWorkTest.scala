package weco.catalogue.display_model.models

import org.scalacheck.ScalacheckShapeless._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.{
  ProductionEventGenerators,
  WorkGenerators
}

class DisplayWorkTest
    extends AnyFunSpec
    with Matchers
    with ProductionEventGenerators
    with WorkGenerators
    with ImageGenerators
    with ScalaCheckPropertyChecks {

  it("parses a Work without any items") {
    val work = indexedWork().items(Nil)

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Items)
    )
    displayWork.items shouldBe Some(List())
  }

  it("parses identified items on a work") {
    val items = createIdentifiedItems(count = 1)
    val work = indexedWork().items(items)

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Items)
    )
    val displayItem = displayWork.items.get.head
    displayItem.id shouldBe Some(items.head.id.canonicalId.underlying)
  }

  it("parses unidentified items on a work") {
    val item = createUnidentifiableItem
    val location = item.locations.head.asInstanceOf[DigitalLocation]
    val work = indexedWork().items(List(item))

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Items)
    )

    val displayItem = displayWork.items.get.head
    displayItem shouldBe DisplayItem(
      id = None,
      identifiers = None,
      locations = List(
        DisplayDigitalLocation(
          DisplayLocationType(location.locationType),
          url = location.url,
          credit = location.credit,
          license = location.license.map(DisplayLicense(_)),
          linkText = location.linkText,
          accessConditions =
            location.accessConditions.map(DisplayAccessCondition(_))
        )
      )
    )
  }

  it("parses a work without any extra identifiers") {
    val work = indexedWork().otherIdentifiers(Nil)

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Identifiers)
    )
    displayWork.identifiers shouldBe Some(
      List(DisplayIdentifier(work.sourceIdentifier))
    )
  }

  it("gets the physicalDescription from a Work") {
    val physicalDescription = "A magnificent mural of magpies"

    val work = indexedWork().physicalDescription(physicalDescription)

    val displayWork = DisplayWork(work)
    displayWork.physicalDescription shouldBe Some(physicalDescription)
  }

  it("gets the format from a Work") {
    val format = Format.Videos

    val expectedDisplayWork = DisplayFormat(
      id = format.id,
      label = format.label
    )

    val work = indexedWork().format(format)

    val displayWork = DisplayWork(work)
    displayWork.workType shouldBe Some(expectedDisplayWork)
  }

  it("gets the ontologyType from a Work") {
    val work = indexedWork().workType(WorkType.Section)
    val displayWork = DisplayWork(work)

    displayWork.ontologyType shouldBe "Section"
  }

  it("gets the languages from a Work") {
    val languages = List(
      Language(id = "bsl", label = "British Sign Language"),
      Language(id = "ger", label = "German")
    )

    val work = indexedWork().languages(languages)

    val noLanguagesInclude = WorksIncludes.none.copy(languages = false)
    val languagesInclude = WorksIncludes.none.copy(languages = true)

    DisplayWork(work, includes = noLanguagesInclude).languages shouldBe None

    val displayWork = DisplayWork(work, includes = languagesInclude)

    displayWork.languages shouldBe Some(
      List(
        DisplayLanguage(id = "bsl", label = "British Sign Language"),
        DisplayLanguage(id = "ger", label = "German")
      )
    )
  }

  it("extracts contributors from a Work with the contributors include") {
    val canonicalId = createCanonicalId
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Person"
    )

    val work = indexedWork().contributors(
      List(
        Contributor(
          agent = Person(
            label = "Vlad the Vanquished",
            id = IdState.Identified(
              canonicalId = canonicalId,
              sourceIdentifier = sourceIdentifier
            )
          ),
          roles = Nil
        ),
        Contributor(
          agent = Organisation(label = "Transylvania Terrors"),
          roles = List(
            ContributionRole(label = "Background location")
          )
        )
      )
    )

    val displayWork =
      DisplayWork(
        work,
        includes =
          WorksIncludes(WorkInclude.Identifiers, WorkInclude.Contributors)
      )

    displayWork.contributors.get shouldBe List(
      DisplayContributor(
        agent = DisplayPerson(
          id = Some(canonicalId.underlying),
          label = "Vlad the Vanquished",
          identifiers = Some(
            List(DisplayIdentifier(sourceIdentifier))
          )
        ),
        roles = List()
      ),
      DisplayContributor(
        agent = DisplayOrganisation(
          id = None,
          label = "Transylvania Terrors",
          identifiers = None
        ),
        roles = List(DisplayContributionRole(label = "Background location"))
      )
    )
  }

  it("extracts production events from a work with the production include") {
    val productionEvent = createProductionEvent

    val work = indexedWork().production(List(productionEvent))

    val displayWork =
      DisplayWork(work, includes = WorksIncludes(WorkInclude.Production))
    displayWork.production.get shouldBe List(
      DisplayProductionEvent(productionEvent, includesIdentifiers = false)
    )
  }

  it("does not extract includes set to false") {
    forAll { work: Work.Visible[WorkState.Indexed] =>
      val displayWork = DisplayWork(work, includes = WorksIncludes.none)

      displayWork.production shouldNot be(defined)
      displayWork.subjects shouldNot be(defined)
      displayWork.genres shouldNot be(defined)
      displayWork.contributors shouldNot be(defined)
      displayWork.items shouldNot be(defined)
      displayWork.identifiers shouldNot be(defined)
    }
  }

  describe("uses the WorksIncludes.identifiers include") {
    val contributorAgentSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Agent"
    )

    val contributorPersonSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Agent"
    )

    val contributorOrganisationSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Agent"
    )

    val subjectSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Subject"
    )

    val conceptSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Concept"
    )

    val periodSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Concept"
    )

    val placeSourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Concept"
    )

    val work = indexedWork()
      .contributors(
        List(
          Contributor(
            agent = Agent(
              label = "Bond",
              id = IdState
                .Identified(
                  createCanonicalId,
                  contributorAgentSourceIdentifier
                )
            ),
            roles = Nil
          ),
          Contributor(
            agent = Organisation(
              label = "Big Business",
              id = IdState.Identified(
                createCanonicalId,
                contributorOrganisationSourceIdentifier
              )
            ),
            roles = Nil
          ),
          Contributor(
            agent = Person(
              label = "Blue Blaise",
              id = IdState
                .Identified(
                  createCanonicalId,
                  contributorPersonSourceIdentifier
                )
            ),
            roles = Nil
          )
        )
      )
      .items(createIdentifiedItems(count = 1))
      .subjects(
        List(
          Subject(
            label = "Beryllium-Boron Bonding",
            id = IdState.Identified(createCanonicalId, subjectSourceIdentifier),
            concepts = List(
              Concept(
                label = "Bonding",
                id = IdState
                  .Identified(createCanonicalId, conceptSourceIdentifier)
              ),
              Period(
                label = "Before",
                id =
                  IdState.Identified(createCanonicalId, periodSourceIdentifier),
                range = None
              ),
              Place(
                label = "Bulgaria",
                id =
                  IdState.Identified(createCanonicalId, placeSourceIdentifier)
              )
            )
          )
        )
      )
      .genres(
        List(
          Genre(
            label = "Black, Brown and Blue",
            concepts = List(
              Concept(
                label = "Colours",
                id =
                  IdState.Identified(createCanonicalId, conceptSourceIdentifier)
              )
            )
          )
        )
      )
      .imageData(
        (1 to 5).map(_ => createImageData.toIdentified).toList
      )

    describe("omits identifiers if WorksIncludes.identifiers is false") {
      val displayWork = DisplayWork(work, includes = WorksIncludes.none)

      it("the top-level Work") {
        displayWork.identifiers shouldBe None
      }

      it("contributors") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(WorkInclude.Contributors))
        val agents: List[DisplayAbstractAgent] =
          displayWork.contributors.get.map { _.agent }
        agents.map { _.identifiers } shouldBe List(None, None, None)
      }

      it("items") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(WorkInclude.Items))
        val item: DisplayItem = displayWork.items.get.head
        item.identifiers shouldBe None
      }

      it("subjects") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(WorkInclude.Subjects))
        val subject = displayWork.subjects.get.head
        subject.identifiers shouldBe None

        val concepts = subject.concepts
        concepts.map { _.identifiers } shouldBe List(None, None, None)
      }

      it("genres") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(WorkInclude.Genres))
        displayWork.genres.get.head.concepts.head.identifiers shouldBe None
      }
    }

    describe("includes identifiers if WorksIncludes.identifiers is true") {
      val displayWork =
        DisplayWork(work, includes = WorksIncludes(WorkInclude.Identifiers))

      it("on the top-level Work") {
        displayWork.identifiers shouldBe Some(
          List(DisplayIdentifier(work.sourceIdentifier))
        )
      }

      it("contributors") {
        val displayWork =
          DisplayWork(
            work,
            includes =
              WorksIncludes(WorkInclude.Contributors, WorkInclude.Identifiers)
          )

        val expectedIdentifiers = List(
          contributorAgentSourceIdentifier,
          contributorOrganisationSourceIdentifier,
          contributorPersonSourceIdentifier
        ).map { identifier =>
          Some(List(DisplayIdentifier(identifier)))
        }

        val agents: List[DisplayAbstractAgent] =
          displayWork.contributors.get.map { _.agent }
        agents.map { _.identifiers } shouldBe expectedIdentifiers
      }

      it("items") {
        val displayWork = DisplayWork(
          work,
          includes = WorksIncludes(WorkInclude.Identifiers, WorkInclude.Items)
        )
        val item: DisplayItem = displayWork.items.get.head
        val identifiedItem =
          work.data.items.head.asInstanceOf[Item[IdState.Identified]]
        item.identifiers shouldBe Some(
          List(DisplayIdentifier(identifiedItem.id.sourceIdentifier))
        )
      }

      it("subjects") {
        val displayWork =
          DisplayWork(
            work,
            includes =
              WorksIncludes(WorkInclude.Identifiers, WorkInclude.Subjects)
          )
        val expectedIdentifiers = List(
          conceptSourceIdentifier,
          periodSourceIdentifier,
          placeSourceIdentifier
        ).map { DisplayIdentifier(_) }
          .map { List(_) }
          .map { Some(_) }

        val subject = displayWork.subjects.get.head
        subject.identifiers shouldBe Some(
          List(DisplayIdentifier(subjectSourceIdentifier))
        )

        val concepts = subject.concepts
        concepts.map { _.identifiers } shouldBe expectedIdentifiers
      }

      it("genres") {
        val displayWork =
          DisplayWork(
            work,
            includes =
              WorksIncludes(WorkInclude.Identifiers, WorkInclude.Genres)
          )
        displayWork.genres.get.head.concepts.head.identifiers shouldBe Some(
          List(DisplayIdentifier(conceptSourceIdentifier))
        )
      }

      it("images") {
        val displayWork = DisplayWork(
          work,
          includes = WorksIncludes(WorkInclude.Images)
        )

        val displayIds = displayWork.images.get.map(_.id)
        val expectedIds = work.data.imageData.map(_.id.canonicalId.underlying)

        displayIds should contain theSameElementsAs expectedIds
      }
    }
  }

  describe("works in a series") {

    it("includes a series in partOf") {
      val work = indexedWork(
        relations = Relations(
          ancestors = List(SeriesRelation("Series A")),
          children = List(),
          siblingsPreceding = List(),
          siblingsSucceeding = List()
        )
      )
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PartOf))
      displayWork.partOf.isEmpty shouldBe false
      val partOf = displayWork.partOf.get
      partOf.flatMap(_.id) shouldBe List()
      partOf.flatMap(_.title) shouldBe List("Series A")

    }

    it("can include multiple series") {
      val work = indexedWork(
        relations = Relations(
          ancestors =
            List(SeriesRelation("Series A"), SeriesRelation("Series B")),
          children = List(),
          siblingsPreceding = List(),
          siblingsSucceeding = List()
        )
      )
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PartOf))
      displayWork.partOf.isEmpty shouldBe false
      val partOf = displayWork.partOf.get
      partOf.flatMap(_.title) shouldBe List("Series B", "Series A")
      // series partOfs have no id.
      partOf.flatMap(_.id) shouldBe List()

    }

    it("includes both series and related works if both are present") {
      val workA = indexedWork()
      val workB = indexedWork()
      val relationA: Relation =
        Relation(work = workA, depth = 2, numChildren = 0, numDescendents = 0)
      val work = indexedWork(
        relations = Relations(
          ancestors = List(
            SeriesRelation("Series A"),
            relationA,
            Relation(
              work = workB,
              depth = 2,
              numChildren = 0,
              numDescendents = 0
            ),
            SeriesRelation("Series B")
          ),
          children = List(),
          siblingsPreceding = List(),
          siblingsSucceeding = List()
        )
      )
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PartOf))
      displayWork.partOf.isEmpty shouldBe false
      val partOf = displayWork.partOf.get
      partOf.flatMap(_.id) shouldBe List(workB.id)
      partOf.flatMap(_.title) shouldBe List(
        workB.data.title.get,
        "Series B",
        "Series A"
      )
      partOf.head.partOf shouldBe Some(
        List(
          DisplayRelation(relationA)
        )
      )
    }

  }

  describe("related works") {
    val workA = indexedWork()
    val workB = indexedWork()
    val workC = indexedWork()
    val workD = indexedWork()
    val workE = indexedWork()
    val workF = indexedWork()

    val relationA = Relation(workA, 0, 1, 5)
    val relationB = Relation(workB, 1, 3, 4)
    val relationC = Relation(workC, 3, 0, 0)
    val relationD = Relation(workD, 3, 0, 0)
    val relationE = Relation(workE, 2, 0, 0)
    val relationF = Relation(workF, 2, 0, 0)

    val work = indexedWork(
      relations = Relations(
        ancestors = List(relationA, relationB),
        children = List(relationE, relationF),
        siblingsPreceding = List(relationC),
        siblingsSucceeding = List(relationD)
      )
    )

    it("includes nested partOf") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PartOf))
      displayWork.partOf.isEmpty shouldBe false
      val partOf = displayWork.partOf.get
      partOf.flatMap(_.id) shouldBe List(workB.state.canonicalId.underlying)
      partOf.head.partOf shouldBe Some(
        List(
          DisplayRelation(relationA)
        )
      )
    }

    it("includes parts") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.Parts))
      displayWork.parts shouldBe Some(
        List(
          DisplayRelation(relationE),
          DisplayRelation(relationF)
        )
      )
    }

    it("includes precededBy") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PrecededBy))
      displayWork.precededBy shouldBe Some(
        List(DisplayRelation(relationC))
      )
    }

    it("includes succeededBy") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.SucceededBy))
      displayWork.succeededBy shouldBe Some(
        List(DisplayRelation(relationD))
      )
    }

    it("does not include relations when not requested") {
      val displayWork = DisplayWork(work, WorksIncludes())
      displayWork.parts shouldBe None
      displayWork.partOf shouldBe None
      displayWork.precededBy shouldBe None
      displayWork.succeededBy shouldBe None
    }
  }
}
