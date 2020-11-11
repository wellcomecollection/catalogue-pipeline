package uk.ac.wellcome.display.models

import java.time.Instant

import org.scalacheck.Arbitrary
import org.scalacheck.Gen.chooseNum
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.models.work.generators.{
  ImageGenerators,
  ProductionEventGenerators,
  WorkGenerators
}
import uk.ac.wellcome.models.work.internal.Format.Videos
import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

class DisplayWorkTest
    extends AnyFunSpec
    with Matchers
    with ProductionEventGenerators
    with WorkGenerators
    with ImageGenerators
    with ScalaCheckPropertyChecks {

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
    val work = identifiedWork().items(Nil)

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Items)
    )
    displayWork.items shouldBe Some(List())
  }

  it("parses identified items on a work") {
    val items = createIdentifiedItems(count = 1)
    val work = identifiedWork().items(items)

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Items)
    )
    val displayItem = displayWork.items.get.head
    displayItem.id shouldBe Some(items.head.id.canonicalId)
  }

  it("parses unidentified items on a work") {
    val item = createUnidentifiableItemWith()
    val location = item.locations.head.asInstanceOf[DigitalLocationDeprecated]
    val work = identifiedWork().items(List(item))

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Items)
    )

    val displayItem = displayWork.items.get.head
    displayItem shouldBe DisplayItem(
      id = None,
      identifiers = None,
      locations = List(
        DisplayDigitalLocationDeprecated(
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
    val work = identifiedWork().otherIdentifiers(Nil)

    val displayWork = DisplayWork(
      work = work,
      includes = WorksIncludes(WorkInclude.Identifiers)
    )
    displayWork.identifiers shouldBe Some(
      List(DisplayIdentifier(work.sourceIdentifier)))
  }

  it("gets the physicalDescription from a Work") {
    val physicalDescription = "A magnificent mural of magpies"

    val work = identifiedWork().physicalDescription(physicalDescription)

    val displayWork = DisplayWork(work)
    displayWork.physicalDescription shouldBe Some(physicalDescription)
  }

  it("gets the format from a Work") {
    val format = Videos

    val expectedDisplayWork = DisplayFormat(
      id = format.id,
      label = format.label
    )

    val work = identifiedWork().format(format)

    val displayWork = DisplayWork(work)
    displayWork.workType shouldBe Some(expectedDisplayWork)
  }

  it("gets the ontologyType from a Work") {
    val work = identifiedWork().workType(WorkType.Section)
    val displayWork = DisplayWork(work)

    displayWork.ontologyType shouldBe "Section"
  }

  it("gets the language from a Work") {
    val language = Language(
      id = Some("bsl"),
      label = "British Sign Language"
    )

    val work = identifiedWork().language(language)

    val displayWork = DisplayWork(work)
    val displayLanguage = displayWork.language.get
    displayLanguage.id shouldBe language.id
    displayLanguage.label shouldBe language.label
  }

  it("gets the languages from a Work") {
    val languages = List(
      Language(id = Some("bsl"), label = "British Sign Language"),
      Language(id = Some("ger"), label = "German")
    )

    val work = identifiedWork().languages(languages)

    val noLanguagesInclude = WorksIncludes().copy(languages = false)
    val languagesInclude = WorksIncludes().copy(languages = true)

    DisplayWork(work, includes = noLanguagesInclude).languages shouldBe None

    val displayWork = DisplayWork(work, includes = languagesInclude)

    displayWork.languages shouldBe Some(
      List(
        DisplayLanguage(id = Some("bsl"), label = "British Sign Language"),
        DisplayLanguage(id = Some("ger"), label = "German")
      )
    )
  }

  it("extracts contributors from a Work with the contributors include") {
    val canonicalId = createCanonicalId
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Person"
    )

    val work = identifiedWork().contributors(
      List(
        Contributor(
          agent = Person(
            label = "Vlad the Vanquished",
            id = IdState.Identified(
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
        includes =
          WorksIncludes(WorkInclude.Identifiers, WorkInclude.Contributors))

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

    val work = identifiedWork().production(List(productionEvent))

    val displayWork =
      DisplayWork(work, includes = WorksIncludes(WorkInclude.Production))
    displayWork.production.get shouldBe List(
      DisplayProductionEvent(productionEvent, includesIdentifiers = false))
  }

  it("does not extract includes set to false") {
    forAll { work: Work.Visible[Identified] =>
      val displayWork = DisplayWork(work, includes = WorksIncludes())

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

    val work = identifiedWork()
      .contributors(
        List(
          Contributor(
            agent = Agent(
              label = "Bond",
              id = IdState
                .Identified(
                  createCanonicalId,
                  contributorAgentSourceIdentifier),
            ),
            roles = Nil
          ),
          Contributor(
            agent = Organisation(
              label = "Big Business",
              id = IdState.Identified(
                createCanonicalId,
                contributorOrganisationSourceIdentifier),
            ),
            roles = Nil
          ),
          Contributor(
            agent = Person(
              label = "Blue Blaise",
              id = IdState
                .Identified(
                  createCanonicalId,
                  contributorPersonSourceIdentifier),
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
                  .Identified(createCanonicalId, conceptSourceIdentifier),
              ),
              Period(
                label = "Before",
                id =
                  IdState.Identified(createCanonicalId, periodSourceIdentifier),
                range = None,
              ),
              Place(
                label = "Bulgaria",
                id =
                  IdState.Identified(createCanonicalId, placeSourceIdentifier),
              )
            )
          ),
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
      .images(
        (1 to 5).map(_ => createUnmergedImage.toIdentified).toList
      )

    describe("omits identifiers if WorksIncludes.identifiers is false") {
      val displayWork = DisplayWork(work, includes = WorksIncludes())

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
          List(DisplayIdentifier(work.sourceIdentifier)))
      }

      it("contributors") {
        val displayWork =
          DisplayWork(
            work,
            includes =
              WorksIncludes(WorkInclude.Contributors, WorkInclude.Identifiers))

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
          includes = WorksIncludes(WorkInclude.Identifiers, WorkInclude.Items))
        val item: DisplayItem = displayWork.items.get.head
        val identifiedItem =
          work.data.items.head.asInstanceOf[Item[IdState.Identified]]
        item.identifiers shouldBe Some(
          List(DisplayIdentifier(identifiedItem.id.sourceIdentifier)))
      }

      it("subjects") {
        val displayWork =
          DisplayWork(
            work,
            includes =
              WorksIncludes(WorkInclude.Identifiers, WorkInclude.Subjects))
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
            includes =
              WorksIncludes(WorkInclude.Identifiers, WorkInclude.Genres))
        displayWork.genres.get.head.concepts.head.identifiers shouldBe Some(
          List(DisplayIdentifier(conceptSourceIdentifier)))
      }

      it("images") {
        val displayWork = DisplayWork(
          work,
          includes = WorksIncludes(WorkInclude.Images)
        )
        displayWork.images.get
          .map(_.id) should contain theSameElementsAs
          work.data.images.map(_.id.canonicalId)
      }
    }
  }

  describe("related works") {
    val work = identifiedWork()
    val workA = identifiedWork()
    val workB = identifiedWork()
    val workC = identifiedWork()
    val workD = identifiedWork()
    val workE = identifiedWork()
    val workF = identifiedWork()

    val relatedWorks = RelatedWorks(
      partOf = Some(
        List(
          RelatedWork(
            workB,
            RelatedWorks.partOf(RelatedWork(workA))
          )
        )
      ),
      parts = Some(List(RelatedWork(workE), RelatedWork(workF))),
      precededBy = Some(List(RelatedWork(workC))),
      succeededBy = Some(List(RelatedWork(workD))),
    )

    it("includes nested partOf") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PartOf), relatedWorks)
      displayWork.partOf.isEmpty shouldBe false
      val partOf: List[DisplayWork] = displayWork.partOf.get
      partOf.map(_.id) shouldBe List(workB.state.canonicalId)
      partOf(0).partOf shouldBe Some(List(DisplayWork(workA)))
    }

    it("includes parts") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.Parts), relatedWorks)
      displayWork.parts shouldBe Some(
        List(DisplayWork(workE), DisplayWork(workF))
      )
    }

    it("includes precededBy") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.PrecededBy), relatedWorks)
      displayWork.precededBy shouldBe Some(List(DisplayWork(workC)))
    }

    it("includes succeededBy") {
      val displayWork =
        DisplayWork(work, WorksIncludes(WorkInclude.SucceededBy), relatedWorks)
      displayWork.succeededBy shouldBe Some(List(DisplayWork(workD)))
    }

    it("does not include relations when not requested") {
      val displayWork = DisplayWork(work, WorksIncludes(), relatedWorks)
      displayWork.parts shouldBe None
      displayWork.partOf shouldBe None
      displayWork.precededBy shouldBe None
      displayWork.succeededBy shouldBe None
    }
  }
}
