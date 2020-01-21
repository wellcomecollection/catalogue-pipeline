package uk.ac.wellcome.display.models.v2

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal._

class DisplayAbstractAgentV2Test
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

    val expectedUnidentifiedAgent: DisplayAgentV2 = DisplayAgentV2(
      id = None,
      identifiers = None,
      label = label
    )

    it(
      "converts an Unidentifiable Agent to a DisplayAgentV2 (includesIdentifiers = true)") {
      DisplayAbstractAgentV2(unidentifiedAgent, includesIdentifiers = true) shouldBe expectedUnidentifiedAgent
    }

    it(
      "converts an Unidentifiable Agent to a DisplayAgentV2 (includesIdentifiers = false)") {
      DisplayAbstractAgentV2(unidentifiedAgent, includesIdentifiers = false) shouldBe expectedUnidentifiedAgent
    }

    it(
      "converts an Identified Agent to a DisplayAgentV2 (includesIdentifiers = true)") {
      val expectedAgent = DisplayAgentV2(
        id = Some(canonicalId),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifierV2(_)
        }),
        label = label
      )

      DisplayAbstractAgentV2(identifiedAgent, includesIdentifiers = true) shouldBe expectedAgent
    }

    it(
      "converts an Identified Agent to a DisplayAgentV2 (includesIdentifiers = false)") {
      val expectedAgent = DisplayAgentV2(
        id = Some(canonicalId),
        identifiers = None,
        label = label
      )

      DisplayAbstractAgentV2(identifiedAgent, includesIdentifiers = false) shouldBe expectedAgent
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

    val expectedUnidentifiedPerson: DisplayPersonV2 = DisplayPersonV2(
      id = None,
      identifiers = None,
      label = label,
      prefix = Some(prefix),
      numeration = Some(numeration)
    )

    it(
      "converts an Unidentifiable Person to a DisplayPersonV2 (includesIdentifiers = true)") {
      DisplayAbstractAgentV2(unidentifiedPerson, includesIdentifiers = true) shouldBe expectedUnidentifiedPerson
    }

    it(
      "converts an Unidentifiable Person to a DisplayPersonV2 (includesIdentifiers = false)") {
      DisplayAbstractAgentV2(unidentifiedPerson, includesIdentifiers = false) shouldBe expectedUnidentifiedPerson
    }

    it(
      "converts an Identified Person to a DisplayPersonV2 (includesIdentifiers = true)") {
      val expectedPerson = DisplayPersonV2(
        id = Some(canonicalId),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifierV2(_)
        }),
        label = label,
        prefix = Some(prefix),
        numeration = Some(numeration)
      )

      DisplayAbstractAgentV2(identifiedPerson, includesIdentifiers = true) shouldBe expectedPerson
    }

    it(
      "converts an Identified Person to a DisplayPersonV2 (includesIdentifiers = false)") {
      val expectedPerson = DisplayPersonV2(
        id = Some(canonicalId),
        identifiers = None,
        label = label,
        prefix = Some(prefix),
        numeration = Some(numeration)
      )

      DisplayAbstractAgentV2(identifiedPerson, includesIdentifiers = false) shouldBe expectedPerson
    }
  }

  describe("Organisation") {
    val unidentifiedOrganisation =
      Organisation(label = label, id = Unidentifiable)

    val identifiedOrganisation = Organisation(label = label, id = identified)

    val expectedUnidentifiedOrganisation: DisplayOrganisationV2 =
      DisplayOrganisationV2(
        id = None,
        identifiers = None,
        label = label
      )

    it(
      "converts an Unidentifiable Organisation to a DisplayOrganisationV2 (includesIdentifiers = true)") {
      DisplayAbstractAgentV2(
        unidentifiedOrganisation,
        includesIdentifiers = true) shouldBe expectedUnidentifiedOrganisation
    }

    it(
      "converts an Unidentifiable Organisation to a DisplayOrganisationV2 (includesIdentifiers = false)") {
      DisplayAbstractAgentV2(
        unidentifiedOrganisation,
        includesIdentifiers = false) shouldBe expectedUnidentifiedOrganisation
    }

    it(
      "converts an Identified Organisation to a DisplayOrganisationV2 (includesIdentifiers = true)") {
      val expectedOrganisation = DisplayOrganisationV2(
        id = Some(canonicalId),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifierV2(_)
        }),
        label = label
      )

      DisplayAbstractAgentV2(identifiedOrganisation, includesIdentifiers = true) shouldBe expectedOrganisation
    }

    it(
      "converts an Identified Organisation to a DisplayOrganisationV2 (includesIdentifiers = false)") {
      val expectedOrganisation = DisplayOrganisationV2(
        id = Some(canonicalId),
        identifiers = None,
        label = label
      )

      DisplayAbstractAgentV2(
        identifiedOrganisation,
        includesIdentifiers = false) shouldBe expectedOrganisation
    }
  }

  describe("Meeting") {
    val unidentifiedMeeting = Meeting(label = label, id = Unidentifiable)

    val identifiedMeeting = Meeting(label = label, id = identified)

    it(
      "converts an Unidentifiable Meeting to a DisplayMeetingV2 (includesIdentifiers = true)") {
      DisplayAbstractAgentV2(unidentifiedMeeting, includesIdentifiers = true) shouldBe
        DisplayMeetingV2(None, None, label)
    }

    it(
      "converts an Unidentifiable Meeting to a DisplayOrganisationV2 (includesIdentifiers = false)") {
      DisplayAbstractAgentV2(unidentifiedMeeting, includesIdentifiers = false) shouldBe
        DisplayMeetingV2(None, None, label)
    }

    it(
      "converts an Identified Meeting to a DisplayMeetingV2 (includesIdentifiers = true)") {
      DisplayAbstractAgentV2(identifiedMeeting, includesIdentifiers = true) shouldBe
        DisplayMeetingV2(
          id = Some(canonicalId),
          identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
            DisplayIdentifierV2(_)
          }),
          label = label
        )
    }

    it(
      "converts an Identified Meeting to a DisplayMeetingV2 (includesIdentifiers = false)") {
      DisplayAbstractAgentV2(identifiedMeeting, includesIdentifiers = false) shouldBe
        DisplayMeetingV2(
          id = Some(canonicalId),
          identifiers = None,
          label = label
        )
    }
  }
}
