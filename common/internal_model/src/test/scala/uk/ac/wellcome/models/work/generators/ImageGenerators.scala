package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  Identifiable,
  IdentifierType,
  MergedImage,
  SourceIdentifier,
  UnmergedImage,
  Unminted
}

trait ImageGenerators extends IdentifiersGenerators with ItemsGenerators {
  def createUnmergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): UnmergedImage[Unminted] = UnmergedImage(
    sourceIdentifier =
      createSourceIdentifierWith(identifierType = identifierType),
    location = location
  )

  def createUnmergedImage: UnmergedImage[Unminted] = createUnmergedImageWith()

  def createMergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    identifierType: IdentifierType = IdentifierType("miro-image-number"),
    parentWork: SourceIdentifier = createSierraSystemSourceIdentifier,
    fullText: Option[String] = None): MergedImage[Unminted] =
    createUnmergedImageWith(location, identifierType) mergeWith (
      parentWork = Identifiable(parentWork),
      fullText = fullText
    )

  def createMergedImage: MergedImage[Unminted] = createMergedImageWith()
}
