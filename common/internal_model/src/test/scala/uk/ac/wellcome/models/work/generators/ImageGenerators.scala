package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

import scala.util.Random

trait ImageGenerators extends IdentifiersGenerators with ItemsGenerators {
  def createUnmergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): UnmergedImage[Identifiable] = UnmergedImage(
    sourceIdentifier =
      createSourceIdentifierWith(identifierType = identifierType),
    version = version,
    location = location
  )

  def createUnmergedImage: UnmergedImage[Identifiable] =
    createUnmergedImageWith()

  def createMergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number"),
    parentWork: SourceIdentifier = createSierraSystemSourceIdentifier,
    fullText: Option[String] = None): MergedImage[Identifiable] =
    createUnmergedImageWith(location, version, identifierType) mergeWith (
      parentWork = Identifiable(parentWork),
      fullText = fullText
    )

  def createMergedImage: MergedImage[Identifiable] = createMergedImageWith()

  def createInferredData = {
    val features1 = (0 until 2048).map(_ => Random.nextFloat() * 100).toList
    val features2 = (0 until 2048).map(_ => Random.nextFloat() * 100).toList
    Some(InferredData(features1, features2, List(randomAlphanumeric(10))))
  }

  def createAugmentedImage(
    inferredData: Option[InferredData] = createInferredData) =
    createMergedImage.toMinted.augment(inferredData)

  implicit class ImageIdOps(val image: MergedImage[Identifiable]) {
    val toMinted: MergedImage[Identified] = MergedImage(
      id = Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = image.id.allSourceIdentifiers.head
      ),
      version = image.version,
      location = image.location,
      parentWork = Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = image.parentWork.allSourceIdentifiers.head
      ),
      fullText = image.fullText
    )
  }
}
