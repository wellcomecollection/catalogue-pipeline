package uk.ac.wellcome.display.models

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal._

class DisplayAbstractAgentTest
    extends FunSpec
    with Matchers
    with IdentifiersGenerators {

  val label: String = randomAlphanumeric(length = 25)
  val sourceIdentifier: SourceIdentifier = createSourceIdentifier
  val otherIdentifiers: List[SourceIdentifier] = (1 to 3).map { _ =>
    createSourceIdentifier
  }.toList
  val canonicalId: String = createCanonicalId

  val identified = Identified(canonicalId, sourceIdentifier, otherIdentifiers)

  describe("Agent") {
    val unidentifiedAgent = Agent(label = label, id = Unidentifiable)

    val identifiedAgent = Agent(label = label, id = identified)

    val expectedUnidentifiedAgent: DisplayAgent = DisplayAgent(
      id = None,
      identifiers = None,
      label = label
    )

    it(
      "converts an Unidentifiable Agent to a DisplayAgentV2 (includesIdentifiers = true)") {
      DisplayAbstractAgent(unidentifiedAgent, includesIdentifiers = true) shouldBe expectedUnidentifiedAgent
    }

    it(
      "converts an Unidentifiable Agent to a DisplayAgentV2 (includesIdentifiers = false)") {
      DisplayAbstractAgent(unidentifiedAgent, includesIdentifiers = false) shouldBe expectedUnidentifiedAgent
    }

    it(
      "converts an Identified Agent to a DisplayAgentV2 (includesIdentifiers = true)") {
      val expectedAgent = models.DisplayAgent(
        id = Some(canonicalId),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifier(_)
        }),
        label = label
      )

      DisplayAbstractAgent(identifiedAgent, includesIdentifiers = true) shouldBe expectedAgent
    }

    it(
      "converts an Identified Agent to a DisplayAgentV2 (includesIdentifiers = false)") {
      val expectedAgent = DisplayAgent(
        id = Some(canonicalId),
        identifiers = None,
        label = label
      )

      DisplayAbstractAgent(identifiedAgent, includesIdentifiers = false) shouldBe expectedAgent
    }
  }

  describe("Person") {
    val prefix = randomAlphanumeric(length = 5)
    val numeration = randomAlphanumeric(length = 3)

    val unidentifiedPerson = Person(
      id = Unidentifiable,
      label = label,
      prefix = Some(prefix),
      numeration = Some(numeration)
    )

    val identifiedPerson = Person(
      id = identified,
      label = label,
      prefix = Some(prefix),
      numeration = Some(numeration)
    )

    val expectedUnidentifiedPerson: DisplayPerson = DisplayPerson(
      id = None,
      identifiers = None,
      label = label,
      prefix = Some(prefix),
      numeration = Some(numeration)
    )

    it(
      "converts an Unidentifiable Person to a DisplayPersonV2 (includesIdentifiers = true)") {
      DisplayAbstractAgent(unidentifiedPerson, includesIdentifiers = true) shouldBe expectedUnidentifiedPerson
    }

    it(
      "converts an Unidentifiable Person to a DisplayPersonV2 (includesIdentifiers = false)") {
      DisplayAbstractAgent(unidentifiedPerson, includesIdentifiers = false) shouldBe expectedUnidentifiedPerson
    }

    it(
      "converts an Identified Person to a DisplayPersonV2 (includesIdentifiers = true)") {
      val expectedPerson = models.DisplayPerson(
        id = Some(canonicalId),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifier(_)
        }),
        label = label,
        prefix = Some(prefix),
        numeration = Some(numeration)
      )

      DisplayAbstractAgent(identifiedPerson, includesIdentifiers = true) shouldBe expectedPerson
    }

    it(
      "converts an Identified Person to a DisplayPersonV2 (includesIdentifiers = false)") {
      val expectedPerson = DisplayPerson(
        id = Some(canonicalId),
        identifiers = None,
        label = label,
        prefix = Some(prefix),
        numeration = Some(numeration)
      )

      DisplayAbstractAgent(identifiedPerson, includesIdentifiers = false) shouldBe expectedPerson
    }
  }

  describe("Organisation") {
    val unidentifiedOrganisation =
      Organisation(label = label, id = Unidentifiable)

    val identifiedOrganisation = Organisation(label = label, id = identified)

    val expectedUnidentifiedOrganisation: DisplayOrganisation =
      DisplayOrganisation(
        id = None,
        identifiers = None,
        label = label
      )

    it(
      "converts an Unidentifiable Organisation to a DisplayOrganisationV2 (includesIdentifiers = true)") {
      DisplayAbstractAgent(unidentifiedOrganisation, includesIdentifiers = true) shouldBe expectedUnidentifiedOrganisation
    }

    it(
      "converts an Unidentifiable Organisation to a DisplayOrganisationV2 (includesIdentifiers = false)") {
      DisplayAbstractAgent(
        unidentifiedOrganisation,
        includesIdentifiers = false) shouldBe expectedUnidentifiedOrganisation
    }

    it(
      "converts an Identified Organisation to a DisplayOrganisationV2 (includesIdentifiers = true)") {
      val expectedOrganisation = models.DisplayOrganisation(
        id = Some(canonicalId),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifier(_)
        }),
        label = label
      )

      DisplayAbstractAgent(identifiedOrganisation, includesIdentifiers = true) shouldBe expectedOrganisation
    }

    it(
      "converts an Identified Organisation to a DisplayOrganisationV2 (includesIdentifiers = false)") {
      val expectedOrganisation = DisplayOrganisation(
        id = Some(canonicalId),
        identifiers = None,
        label = label
      )

      DisplayAbstractAgent(identifiedOrganisation, includesIdentifiers = false) shouldBe expectedOrganisation
    }
  }

  describe("Meeting") {
    val unidentifiedMeeting = Meeting(label = label, id = Unidentifiable)

    val identifiedMeeting = Meeting(label = label, id = identified)

    it(
      "converts an Unidentifiable Meeting to a DisplayMeetingV2 (includesIdentifiers = true)") {
      DisplayAbstractAgent(unidentifiedMeeting, includesIdentifiers = true) shouldBe
        DisplayMeeting(None, None, label)
    }

    it(
      "converts an Unidentifiable Meeting to a DisplayOrganisationV2 (includesIdentifiers = false)") {
      DisplayAbstractAgent(unidentifiedMeeting, includesIdentifiers = false) shouldBe
        DisplayMeeting(None, None, label)
    }

    it(
      "converts an Identified Meeting to a DisplayMeetingV2 (includesIdentifiers = true)") {
      DisplayAbstractAgent(identifiedMeeting, includesIdentifiers = true) shouldBe
        models.DisplayMeeting(
          id = Some(canonicalId),
          identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
            DisplayIdentifier(_)
          }),
          label = label
        )
    }

    it(
      "converts an Identified Meeting to a DisplayMeetingV2 (includesIdentifiers = false)") {
      DisplayAbstractAgent(identifiedMeeting, includesIdentifiers = false) shouldBe
        DisplayMeeting(
          id = Some(canonicalId),
          identifiers = None,
          label = label
        )
    }
  }
}
