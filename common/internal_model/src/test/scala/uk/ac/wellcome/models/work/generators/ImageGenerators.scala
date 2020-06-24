package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

import scala.util.Random

trait ImageGenerators extends IdentifiersGenerators with ItemsGenerators {
  def createUnmergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): UnmergedImage[Identifiable, Unminted] = UnmergedImage(
    sourceIdentifier =
      createSourceIdentifierWith(identifierType = identifierType),
    version = version,
    location = location
  )

  def createUnmergedImage: UnmergedImage[Identifiable, Unminted] =
    createUnmergedImageWith()

  def createMergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number"),
    parentWork: SourceIdentifier = createSierraSystemSourceIdentifier): MergedImage[Identifiable, Unminted] =
    createUnmergedImageWith(location, version, identifierType) mergeWith (
      sourceWork = Identifiable(parentWork),
      WorkData()
    )

  def createMergedImage: MergedImage[Identifiable, Unminted] = createMergedImageWith()

  def createIdentifiedMergedImageWith(
                                       imageId: Identified = Identified(createCanonicalId, createSourceIdentifier),
                                       workId: Identified= Identified(createCanonicalId, createSourceIdentifier),
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1): MergedImage[Identified, Minted] = {
    MergedImage[Identified, Minted](imageId, version, location , SourceWorks[Identified, Minted](SourceWork(workId, WorkData[Minted, Identified]()), None))
//    createUnmergedImageWith(location, version, identifierType) mergeWith (
//      sourceWork = Identified(createCanonicalId,parentWork),
//      WorkData()
//    )
  }

  def createInferredData = {
    val features1 = (0 until 2048).map(_ => Random.nextFloat() * 100).toList
    val features2 = (0 until 2048).map(_ => Random.nextFloat() * 100).toList
    def randIdx = Random.nextInt(256)
    val lshEncodedFeatures =
      (0 until 256)
        .map(_ => s"$randIdx-$randIdx")
        .toList
    Some(InferredData(features1, features2, lshEncodedFeatures))
  }

  def createAugmentedImageWith(
     imageId: Identified = Identified(createCanonicalId, createSourceIdentifierWith(IdentifierType("miro-image-number"))),
    workId: Identified= Identified(createCanonicalId, createSierraSystemSourceIdentifier),
    inferredData: Option[InferredData] = createInferredData,
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
  ) =
    createIdentifiedMergedImageWith(
      imageId,
      workId,
      location,
      version
    ).augment(inferredData)

  def createAugmentedImage(): AugmentedImage = createAugmentedImageWith()

  def createLicensedImage(license: License): AugmentedImage =
    createAugmentedImageWith(
      location = createDigitalLocationWith(license = Some(license))
    )

  // Create a set of images with intersecting LSH lists to ensure
  // that similarity queries will return something
  def createVisuallySimilarImages(n: Int): Seq[AugmentedImage] = {
    val baseFeatures = createInferredData.get.lshEncodedFeatures
    val similarFeatures = (2 to n).map { n =>
      val mergeIdx = n % baseFeatures.size
      baseFeatures.drop(mergeIdx) ++
        createInferredData.get.lshEncodedFeatures.take(mergeIdx)
    }
    (similarFeatures :+ baseFeatures).map { features =>
      createAugmentedImageWith(
        inferredData = createInferredData.map {
          _.copy(
            lshEncodedFeatures = features
          )
        }
      )
    }
  }

  implicit class UnmergedImageIdOps(val image: UnmergedImage[Identifiable, Unminted]) {
    def toIdentifiedWith(
      id: String = createCanonicalId): UnmergedImage[Identified, Minted] =
      UnmergedImage(
        id = Identified(
          canonicalId = id,
          sourceIdentifier = image.id.allSourceIdentifiers.head
        ),
        version = image.version,
        location = image.location
      )

    val toIdentified: UnmergedImage[Identified, Minted] = toIdentifiedWith()
  }
}
