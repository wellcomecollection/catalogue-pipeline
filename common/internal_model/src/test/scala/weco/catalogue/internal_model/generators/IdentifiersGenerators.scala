package weco.catalogue.internal_model.generators

import uk.ac.wellcome.fixtures.RandomGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalID,
  IdentifierType,
  SourceIdentifier
}

import scala.util.Random

trait IdentifiersGenerators extends RandomGenerators {
  def createCanonicalId: CanonicalID =
    CanonicalID(randomAlphanumeric(length = 8).toLowerCase())

  def createSourceIdentifier: SourceIdentifier = createSourceIdentifierWith()

  def createSourceIdentifierWith(
    identifierType: IdentifierType = chooseFrom(
      IdentifierType.MiroImageNumber,
      IdentifierType.SierraSystemNumber,
      IdentifierType.CalmRecordIdentifier
    ),
    value: String = randomAlphanumeric(length = 10),
    ontologyType: String = "Work"): SourceIdentifier =
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

  def randomMiroId(prefix: Char = chooseFrom(miroIdPrefixes: _*),
                   length: Int = 8): String =
    s"%c%0${length - 1}d".format(
      prefix,
      Random.nextInt(math.pow(10, length - 1).toInt)
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

  def createDigcodeIdentifier(digcode: String): SourceIdentifier =
    SourceIdentifier(
      value = digcode,
      identifierType = IdentifierType.WellcomeDigcode,
      ontologyType = "Work"
    )
}
