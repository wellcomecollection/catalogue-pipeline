package weco.catalogue.internal_model.generators

import org.scalacheck.Arbitrary
import weco.fixtures.RandomGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdentifierType, SourceIdentifier}

trait IdentifiersGenerators extends RandomGenerators {
  implicit val arbitraryCanonicalId: Arbitrary[CanonicalId] =
    Arbitrary {
      createCanonicalId
    }

  // We have a rule that says SourceIdentifier isn't allowed to contain whitespace,
  // but sometimes scalacheck will happen to generate such a string, which breaks
  // tests in CI.  This generator is meant to create SourceIdentifiers that
  // don't contain whitespace.
  implicit val arbitrarySourceIdentifier: Arbitrary[SourceIdentifier] =
    Arbitrary {
      createSourceIdentifier
    }

  def createCanonicalId: CanonicalId =
    CanonicalId(createUnderlyingCanonicalId)

  def createUnderlyingCanonicalId: String =
    randomAlphanumeric(length = 8).toLowerCase()

  def createSourceIdentifier: SourceIdentifier = createSourceIdentifierWith()

  def createSourceIdentifierWith(
    identifierType: IdentifierType = chooseFrom(
      IdentifierType.MiroImageNumber,
      IdentifierType.SierraSystemNumber,
      IdentifierType.CalmRecordIdentifier,
      // We deliberately omit Tei & Ebsco identifiers here so that we
      // have predictable merge behaviour in tests. Miro & Calm records
      // are always merged to to Sierra, and this is the relationship
      // we assume in tests that use this generator.
    ),
    value: String = randomAlphanumeric(length = 10),
    ontologyType: String = "Work"
  ): SourceIdentifier =
    SourceIdentifier(
      identifierType = identifierType,
      value = value,
      ontologyType = ontologyType
    )

  def createSierraSystemSourceIdentifierWith(
    value: String = randomAlphanumeric(length = 10),
    ontologyType: String = "Work"
  ): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.SierraSystemNumber,
      value = value,
      ontologyType = ontologyType
    )

  def createMetsSourceIdentifier: SourceIdentifier =
    createSourceIdentifierWith(identifierType = IdentifierType.METS)

  def createSierraSystemSourceIdentifier: SourceIdentifier =
    createSierraSystemSourceIdentifierWith()

  def createSierraIdentifierSourceIdentifierWith(
    value: String = randomAlphanumeric(length = 10),
    ontologyType: String = "Work"
  ): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.SierraIdentifier,
      value = value,
      ontologyType = ontologyType
    )

  def createSierraIdentifierSourceIdentifier: SourceIdentifier =
    createSierraIdentifierSourceIdentifierWith()

  def createIsbnSourceIdentifier: SourceIdentifier =
    createSourceIdentifierWith(
      identifierType = IdentifierType.ISBN
    )

  private val miroIdPrefixes: Seq[Char] = Seq(
    'C', 'L', 'V', 'W', 'N', 'M', 'B', 'A', 'S', 'F', 'D'
  )

  def randomMiroId(
    prefix: Char = chooseFrom(miroIdPrefixes: _*),
    length: Int = 8
  ): String =
    s"%c%0${length - 1}d".format(
      prefix,
      random.nextInt(math.pow(10, length - 1).toInt)
    )

  def createMiroSourceIdentifierWith(
    value: String = randomMiroId(),
    ontologyType: String = "Work"
  ): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.MiroImageNumber,
      ontologyType = ontologyType,
      value = value
    )

  def createMiroSourceIdentifier: SourceIdentifier =
    createMiroSourceIdentifierWith()

  def createCalmRecordID: String =
    randomUUID.toString

  def createCalmSourceIdentifier: SourceIdentifier =
    SourceIdentifier(
      value = createCalmRecordID,
      identifierType = IdentifierType.CalmRecordIdentifier,
      ontologyType = "Work"
    )

  def createTeiSourceIdentifier: SourceIdentifier =
    SourceIdentifier(
      value = randomAlphanumeric(10),
      identifierType = IdentifierType.Tei,
      ontologyType = "Work"
    )

  def createDigcodeIdentifier(digcode: String): SourceIdentifier =
    SourceIdentifier(
      value = digcode,
      identifierType = IdentifierType.WellcomeDigcode,
      ontologyType = "Work"
    )
}
