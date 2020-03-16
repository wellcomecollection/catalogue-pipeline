package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  Identifiable,
  Identified,
  IdentifierType,
  MergedImage,
  Minted,
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

  implicit class ImageIdOps(val image: MergedImage[Unminted]) {
    val toMinted: MergedImage[Minted] = MergedImage(
      id = Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = image.id.allSourceIdentifiers.head
      ),
      location = image.location,
      parentWork = Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = image.parentWork.allSourceIdentifiers.head
      ),
      fullText = image.fullText
    )
  }
}
