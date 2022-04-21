package weco.catalogue.display_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.display_model.identifiers.DisplayIdentifier
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.locations._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.{ProductionEventGenerators, WorkGenerators}

class DisplayWorkTest
    extends AnyFunSpec
    with Matchers
    with ProductionEventGenerators
    with WorkGenerators
    with ImageGenerators {

  it("parses a Work without any items") {
    val work = indexedWork().items(Nil)

    val displayWork = DisplayWork(work)
    displayWork.items shouldBe List()
  }

  it("parses identified items on a work") {
    val items = createIdentifiedItems(count = 1)
    val work = indexedWork().items(items)

    val displayWork = DisplayWork(work)
    val displayItem = displayWork.items.head
    displayItem.id shouldBe Some(items.head.id.canonicalId.underlying)
  }

  it("parses unidentified items on a work") {
    val item = createUnidentifiableItem
    val location = item.locations.head.asInstanceOf[DigitalLocation]
    val work = indexedWork().items(List(item))

    val displayWork = DisplayWork(work)

    val displayItem = displayWork.items.head
    displayItem shouldBe DisplayItem(
      id = None,
      identifiers = Nil,
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

    val displayWork = DisplayWork(work)

    displayWork.identifiers shouldBe List(DisplayIdentifier(work.sourceIdentifier))
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

    val displayWork = DisplayWork(work)

    displayWork.languages shouldBe List(
      DisplayLanguage(id = "bsl", label = "British Sign Language"),
      DisplayLanguage(id = "ger", label = "German")
    )
  }

  it("extracts contributors from a Work") {
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

    val displayWork = DisplayWork(work)

    displayWork.contributors shouldBe List(
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

  it("extracts production events from a work") {
    val productionEvent = createProductionEvent

    val work = indexedWork().production(List(productionEvent))

    val displayWork = DisplayWork(work)
    displayWork.production shouldBe List(DisplayProductionEvent(productionEvent))
  }

  describe("extracts identifiers") {
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

    val displayWork = DisplayWork(work)

    it("on the top-level Work") {
      displayWork.identifiers shouldBe List(DisplayIdentifier(work.sourceIdentifier))
    }

    it("contributors") {
      val expectedIdentifiers = List(
        contributorAgentSourceIdentifier,
        contributorOrganisationSourceIdentifier,
        contributorPersonSourceIdentifier
      ).map { identifier =>
        Some(List(DisplayIdentifier(identifier)))
      }

      val agents: List[DisplayAbstractAgent] =
        displayWork.contributors.map { _.agent }
      agents.map { _.identifiers } shouldBe expectedIdentifiers
    }

    it("items") {
      val item: DisplayItem = displayWork.items.head
      val identifiedItem =
        work.data.items.head.asInstanceOf[Item[IdState.Identified]]
      item.identifiers shouldBe List(DisplayIdentifier(identifiedItem.id.sourceIdentifier))
    }

    it("subjects") {
      val expectedIdentifiers = List(
        conceptSourceIdentifier,
        periodSourceIdentifier,
        placeSourceIdentifier
      ).map {
        DisplayIdentifier(_)
      }
        .map {
          List(_)
        }
        .map {
          Some(_)
        }

      val subject = displayWork.subjects.head
      subject.identifiers shouldBe Some(List(DisplayIdentifier(subjectSourceIdentifier)))

      val concepts = subject.concepts
      concepts.map {
        _.identifiers
      } shouldBe expectedIdentifiers
    }

    it("genres") {
      displayWork.genres.head.concepts.head.identifiers shouldBe Some(List(DisplayIdentifier(conceptSourceIdentifier)))
    }

    it("images") {
      val displayIds = displayWork.images.map(_.id)
      val expectedIds = work.data.imageData.map(_.id.canonicalId.underlying)

      displayIds should contain theSameElementsAs expectedIds
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
      val displayWork = DisplayWork(work)

      displayWork.partOf.flatMap(_.id) shouldBe List()
      displayWork.partOf.flatMap(_.title) shouldBe List("Series A")
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
      val displayWork = DisplayWork(work)

      displayWork.partOf.flatMap(_.title) shouldBe List("Series B", "Series A")
      // series partOfs have no id.
      displayWork.partOf.flatMap(_.id) shouldBe List()
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
      val displayWork = DisplayWork(work)
      displayWork.partOf.isEmpty shouldBe false

      displayWork.partOf.flatMap(_.id) shouldBe List(workB.id)
      displayWork.partOf.flatMap(_.title) shouldBe List(
        workB.data.title.get,
        "Series B",
        "Series A"
      )
      displayWork.partOf.head.partOf shouldBe Some(
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
      val displayWork = DisplayWork(work)

      val partOf = displayWork.partOf
      partOf.flatMap(_.id) shouldBe List(workB.state.canonicalId.underlying)
      partOf.head.partOf shouldBe Some(List(DisplayRelation(relationA)))
    }

    it("includes parts") {
      val displayWork = DisplayWork(work)
      displayWork.parts shouldBe List(
        DisplayRelation(relationE),
        DisplayRelation(relationF)
      )
    }

    it("includes precededBy") {
      val displayWork = DisplayWork(work)
      displayWork.precededBy shouldBe List(DisplayRelation(relationC))
    }

    it("includes succeededBy") {
      val displayWork = DisplayWork(work)
      displayWork.succeededBy shouldBe List(DisplayRelation(relationD))
    }
  }
}
