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
      sourceWork = Identifiable(parentWork),
      fullText = fullText
    )

  def createMergedImage: MergedImage[Identifiable] = createMergedImageWith()

  def createInferredData = {
    val features1 = (0 until 2048).map(_ => Random.nextFloat() * 100).toList
    val features2 = (0 until 2048).map(_ => Random.nextFloat() * 100).toList
    val lshEncodedFeatures =
      (0 until 256).map(_ => randomAlphanumeric(3)).toList
    Some(InferredData(features1, features2, lshEncodedFeatures))
  }

  def createAugmentedImageWith(
    id: String = createCanonicalId,
    inferredData: Option[InferredData] = createInferredData,
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number"),
    parentWork: SourceIdentifier = createSierraSystemSourceIdentifier,
    fullText: Option[String] = None
  ) =
    createMergedImageWith(
      location,
      version,
      identifierType,
      parentWork,
      fullText
    ).toIdentifiedWith(id).augment(inferredData)

  def createAugmentedImage(): AugmentedImage = createAugmentedImageWith()

  def createLicensedImage(license: License): AugmentedImage =
    createAugmentedImageWith(
      location = createDigitalLocationWith(license = Some(license))
    )

  implicit class MergedImageIdOps(val image: MergedImage[Identifiable]) {
    def toIdentifiedWith(
      id: String = createCanonicalId,
      parentId: String = createCanonicalId): MergedImage[Identified] =
      MergedImage(
        id = Identified(
          canonicalId = id,
          sourceIdentifier = image.id.allSourceIdentifiers.head
        ),
        version = image.version,
        location = image.location,
        source = SourceWork(
          Identified(
            canonicalId = parentId,
            sourceIdentifier = image.source.id.sourceIdentifier
          )
        ),
        fullText = image.fullText
      )

    val toIdentified: MergedImage[Identified] = toIdentifiedWith()
  }

  implicit class UnmergedImageIdOps(val image: UnmergedImage[Identifiable]) {
    def toIdentifiedWith(
      id: String = createCanonicalId): UnmergedImage[Identified] =
      UnmergedImage(
        id = Identified(
          canonicalId = id,
          sourceIdentifier = image.id.allSourceIdentifiers.head
        ),
        version = image.version,
        location = image.location
      )

    val toIdentified: UnmergedImage[Identified] = toIdentifiedWith()
  }
}
