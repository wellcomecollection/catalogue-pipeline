package uk.ac.wellcome.display.models

import java.time.Instant

import org.scalacheck.Arbitrary
import org.scalacheck.Gen.chooseNum
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.{
  ProductionEventGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal.WorkType.Videos
import uk.ac.wellcome.models.work.internal._

class DisplayWorkTest
    extends FunSpec
    with Matchers
    with ProductionEventGenerators
    with WorksGenerators
    with PropertyChecks {

  // We use this for the scalacheck of the java.time.Instant type
  // We could just import the library, but I might wait until we need more
  // Taken from here:
  // https://github.com/rallyhealth/scalacheck-ops/blob/master/core/src/main/scala/org/scalacheck/ops/time/ImplicitJavaTimeGenerators.scala
  implicit val arbInstant: Arbitrary[Instant] =
    Arbitrary {
      for {
        millis <- chooseNum(
          Instant.MIN.getEpochSecond,
          Instant.MAX.getEpochSecond)
        nanos <- chooseNum(Instant.MIN.getNano, Instant.MAX.getNano)
      } yield {
        Instant.ofEpochMilli(millis).plusNanos(nanos)
      }
    }

  it("parses a Work without any items") {
    val work = createIdentifiedWorkWith(
      items = List()
    )

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(items = true)
    )
    displayWork.items shouldBe Some(List())
  }

  it("parses identified items on a work") {
    val items = createIdentifiedItems(count = 1)
    val work = createIdentifiedWorkWith(
      items = items
    )

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(items = true)
    )
    val displayItem = displayWork.items.get.head
    displayItem.id shouldBe Some(items.head.id.canonicalId)
  }

  it("parses unidentified items on a work") {
    val item = createUnidentifiableItemWith()
    val location = item.locations.head.asInstanceOf[DigitalLocation]
    val work = createIdentifiedWorkWith(items = List(item))

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(items = true)
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
          accessConditions =
            location.accessConditions.map(DisplayAccessCondition(_))
        )
      )
    )
  }

  it("parses a work without any extra identifiers") {
    val work = createIdentifiedWorkWith(
      otherIdentifiers = List()
    )

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(identifiers = true)
    )
    displayWork.identifiers shouldBe Some(
      List(DisplayIdentifier(work.sourceIdentifier)))
  }

  it("gets the physicalDescription from a Work") {
    val physicalDescription = "A magnificent mural of magpies"

    val work = createIdentifiedWorkWith(
      physicalDescription = Some(physicalDescription)
    )

    val displayWork = DisplayWork(work)
    displayWork.physicalDescription shouldBe Some(physicalDescription)
  }

  it("gets the workType from a Work") {
    val workType = Videos

    val expectedDisplayWork = DisplayWorkType(
      id = workType.id,
      label = workType.label
    )

    val work = createIdentifiedWorkWith(
      workType = Some(workType)
    )

    val displayWork = DisplayWork(work)
    displayWork.workType shouldBe Some(expectedDisplayWork)
  }

  it("gets the language from a Work") {
    val language = Language(
      id = Some("bsl"),
      label = "British Sign Language"
    )

    val work = createIdentifiedWorkWith(
      language = Some(language)
    )

    val displayWork = DisplayWork(work)
    val displayLanguage = displayWork.language.get
    displayLanguage.id shouldBe language.id
    displayLanguage.label shouldBe language.label
  }

  it("extracts contributors from a Work with the contributors include") {
    val canonicalId = createCanonicalId
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Person"
    )

    val work = createIdentifiedWorkWith(
      contributors = List(
        Contributor(
          agent = Person(
            label = "Vlad the Vanquished",
            id = Identified(
              canonicalId = canonicalId,
              sourceIdentifier = sourceIdentifier
            )
          ),
          roles = Nil,
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
        includes = WorksIncludes(identifiers = true, contributors = true))

    displayWork.contributors.get shouldBe List(
      DisplayContributor(
        agent = DisplayPerson(
          id = Some(canonicalId),
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

    val work = createIdentifiedWorkWith(
      production = List(productionEvent)
    )

    val displayWork =
      DisplayWork(work, includes = WorksIncludes(production = true))
    displayWork.production.get shouldBe List(
      DisplayProductionEvent(productionEvent, includesIdentifiers = false))
  }

  it("does not extract includes set to false") {
    forAll { work: IdentifiedWork =>
      val displayWork =
        DisplayWork(work, includes = WorksIncludes())

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

    val work = createIdentifiedWorkWith(
      contributors = List(
        Contributor(
          agent =
            Agent(
              label = "Bond",
              id =
                Identified(createCanonicalId, contributorAgentSourceIdentifier),
            ),
          roles = Nil
        ),
        Contributor(
          agent = Organisation(
            label = "Big Business",
            id = Identified(
              createCanonicalId,
              contributorOrganisationSourceIdentifier),
          ),
          roles = Nil
        ),
        Contributor(
          agent = Person(
            label = "Blue Blaise",
            id =
              Identified(createCanonicalId, contributorPersonSourceIdentifier),
          ),
          roles = Nil
        )
      ),
      items = createIdentifiedItems(count = 1),
      subjects = List(
        Subject(
          label = "Beryllium-Boron Bonding",
          id = Identified(createCanonicalId, subjectSourceIdentifier),
          concepts = List(
            Concept(
              label = "Bonding",
              id = Identified(createCanonicalId, conceptSourceIdentifier),
            ),
            Period(
              label = "Before",
              id = Identified(createCanonicalId, periodSourceIdentifier),
              range = None,
            ),
            Place(
              label = "Bulgaria",
              id = Identified(createCanonicalId, placeSourceIdentifier),
            )
          )
        ),
      ),
      genres = List(
        Genre(
          label = "Black, Brown and Blue",
          concepts = List(
            Concept(
              label = "Colours",
              id = Identified(createCanonicalId, conceptSourceIdentifier)
            )
          )
        )
      )
    )

    describe("omits identifiers if WorksIncludes.identifiers is false") {
      val displayWork = DisplayWork(work, includes = WorksIncludes())

      it("the top-level Work") {
        displayWork.identifiers shouldBe None
      }

      it("contributors") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(contributors = true))
        val agents: List[DisplayAbstractAgent] =
          displayWork.contributors.get.map { _.agent }
        agents.map { _.identifiers } shouldBe List(None, None, None)
      }

      it("items") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(items = true))
        val item: DisplayItem = displayWork.items.get.head
        item.identifiers shouldBe None
      }

      it("subjects") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(subjects = true))
        val subject = displayWork.subjects.get.head
        subject.identifiers shouldBe None

        val concepts = subject.concepts
        concepts.map { _.identifiers } shouldBe List(None, None, None)
      }

      it("genres") {
        val displayWork =
          DisplayWork(work, includes = WorksIncludes(genres = true))
        displayWork.genres.get.head.concepts.head.identifiers shouldBe None
      }
    }

    describe("includes identifiers if WorksIncludes.identifiers is true") {
      val displayWork =
        DisplayWork(work, includes = WorksIncludes(identifiers = true))

      it("on the top-level Work") {
        displayWork.identifiers shouldBe Some(
          List(DisplayIdentifier(work.sourceIdentifier)))
      }

      it("contributors") {
        val displayWork =
          DisplayWork(
            work,
            includes = WorksIncludes(contributors = true, identifiers = true))

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
          includes = WorksIncludes(identifiers = true, items = true))
        val item: DisplayItem = displayWork.items.get.head
        val identifiedItem = work.data.items.head.asInstanceOf[Item[Identified]]
        item.identifiers shouldBe Some(
          List(DisplayIdentifier(identifiedItem.id.sourceIdentifier)))
      }

      it("subjects") {
        val displayWork =
          DisplayWork(
            work,
            includes = WorksIncludes(identifiers = true, subjects = true))
        val expectedIdentifiers = List(
          conceptSourceIdentifier,
          periodSourceIdentifier,
          placeSourceIdentifier
        ).map { DisplayIdentifier(_) }
          .map { List(_) }
          .map { Some(_) }

        val subject = displayWork.subjects.get.head
        subject.identifiers shouldBe Some(
          List(DisplayIdentifier(subjectSourceIdentifier)))

        val concepts = subject.concepts
        concepts.map { _.identifiers } shouldBe expectedIdentifiers
      }

      it("genres") {
        val displayWork =
          DisplayWork(
            work,
            includes = WorksIncludes(identifiers = true, genres = true))
        displayWork.genres.get.head.concepts.head.identifiers shouldBe Some(
          List(DisplayIdentifier(conceptSourceIdentifier)))
      }
    }
  }
}
