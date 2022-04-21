package weco.catalogue.display_model.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Agent, Meeting, Organisation, Person}

class DisplayAbstractAgentTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators {

  val label: String = randomAlphanumeric(length = 25)
  val sourceIdentifier: SourceIdentifier = createSourceIdentifier
  val otherIdentifiers: List[SourceIdentifier] = (1 to 3).map { _ =>
    createSourceIdentifier
  }.toList
  val canonicalId: CanonicalId = createCanonicalId

  val identified =
    IdState.Identified(canonicalId, sourceIdentifier, otherIdentifiers)

  describe("Agent") {
    val unidentifiedAgent = Agent(label = label, id = IdState.Unidentifiable)

    val identifiedAgent = Agent(label = label, id = identified)

    val expectedUnidentifiedAgent: DisplayAgent = DisplayAgent(
      id = None,
      identifiers = None,
      label = label
    )

    it(
      "converts an Unidentifiable Agent to a DisplayAgent (includesIdentifiers = true)"
    ) {
      DisplayAbstractAgent(unidentifiedAgent, includesIdentifiers = true) shouldBe expectedUnidentifiedAgent
    }

    it(
      "converts an Unidentifiable Agent to a DisplayAgent (includesIdentifiers = false)"
    ) {
      DisplayAbstractAgent(unidentifiedAgent, includesIdentifiers = false) shouldBe expectedUnidentifiedAgent
    }

    it(
      "converts an Identified Agent to a DisplayAgent (includesIdentifiers = true)"
    ) {
      val expectedAgent = DisplayAgent(
        id = Some(canonicalId.underlying),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifier(_)
        }),
        label = label
      )

      DisplayAbstractAgent(identifiedAgent, includesIdentifiers = true) shouldBe expectedAgent
    }

    it(
      "converts an Identified Agent to a DisplayAgent (includesIdentifiers = false)"
    ) {
      val expectedAgent = DisplayAgent(
        id = Some(canonicalId.underlying),
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
      id = IdState.Unidentifiable,
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
      "converts an Unidentifiable Person to a DisplayPerson (includesIdentifiers = true)"
    ) {
      DisplayAbstractAgent(unidentifiedPerson, includesIdentifiers = true) shouldBe expectedUnidentifiedPerson
    }

    it(
      "converts an Unidentifiable Person to a DisplayPerson (includesIdentifiers = false)"
    ) {
      DisplayAbstractAgent(unidentifiedPerson, includesIdentifiers = false) shouldBe expectedUnidentifiedPerson
    }

    it(
      "converts an Identified Person to a DisplayPerson (includesIdentifiers = true)"
    ) {
      val expectedPerson = DisplayPerson(
        id = Some(canonicalId.underlying),
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
      "converts an Identified Person to a DisplayPerson (includesIdentifiers = false)"
    ) {
      val expectedPerson = DisplayPerson(
        id = Some(canonicalId.underlying),
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
      Organisation(label = label, id = IdState.Unidentifiable)

    val identifiedOrganisation = Organisation(label = label, id = identified)

    val expectedUnidentifiedOrganisation: DisplayOrganisation =
      DisplayOrganisation(
        id = None,
        identifiers = None,
        label = label
      )

    it(
      "converts an Unidentifiable Organisation to a DisplayOrganisation (includesIdentifiers = true)"
    ) {
      DisplayAbstractAgent(unidentifiedOrganisation, includesIdentifiers = true) shouldBe expectedUnidentifiedOrganisation
    }

    it(
      "converts an Unidentifiable Organisation to a DisplayOrganisation (includesIdentifiers = false)"
    ) {
      DisplayAbstractAgent(
        unidentifiedOrganisation,
        includesIdentifiers = false
      ) shouldBe expectedUnidentifiedOrganisation
    }

    it(
      "converts an Identified Organisation to a DisplayOrganisation (includesIdentifiers = true)"
    ) {
      val expectedOrganisation = DisplayOrganisation(
        id = Some(canonicalId.underlying),
        identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
          DisplayIdentifier(_)
        }),
        label = label
      )

      DisplayAbstractAgent(identifiedOrganisation, includesIdentifiers = true) shouldBe expectedOrganisation
    }

    it(
      "converts an Identified Organisation to a DisplayOrganisation (includesIdentifiers = false)"
    ) {
      val expectedOrganisation = DisplayOrganisation(
        id = Some(canonicalId.underlying),
        identifiers = None,
        label = label
      )

      DisplayAbstractAgent(identifiedOrganisation, includesIdentifiers = false) shouldBe expectedOrganisation
    }
  }

  describe("Meeting") {
    val unidentifiedMeeting =
      Meeting(label = label, id = IdState.Unidentifiable)

    val identifiedMeeting = Meeting(label = label, id = identified)

    it(
      "converts an Unidentifiable Meeting to a DisplayMeeting (includesIdentifiers = true)"
    ) {
      DisplayAbstractAgent(unidentifiedMeeting, includesIdentifiers = true) shouldBe
        DisplayMeeting(None, None, label)
    }

    it(
      "converts an Unidentifiable Meeting to a DisplayOrganisation (includesIdentifiers = false)"
    ) {
      DisplayAbstractAgent(unidentifiedMeeting, includesIdentifiers = false) shouldBe
        DisplayMeeting(None, None, label)
    }

    it(
      "converts an Identified Meeting to a DisplayMeeting (includesIdentifiers = true)"
    ) {
      DisplayAbstractAgent(identifiedMeeting, includesIdentifiers = true) shouldBe
        DisplayMeeting(
          id = Some(canonicalId.underlying),
          identifiers = Some((List(sourceIdentifier) ++ otherIdentifiers).map {
            DisplayIdentifier(_)
          }),
          label = label
        )
    }

    it(
      "converts an Identified Meeting to a DisplayMeeting (includesIdentifiers = false)"
    ) {
      DisplayAbstractAgent(identifiedMeeting, includesIdentifiers = false) shouldBe
        DisplayMeeting(
          id = Some(canonicalId.underlying),
          identifiers = None,
          label = label
        )
    }
  }
}
