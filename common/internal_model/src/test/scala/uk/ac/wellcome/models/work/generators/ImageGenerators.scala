package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

import scala.util.Random

trait ImageGenerators extends IdentifiersGenerators with ItemsGenerators with WorksGenerators {
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

  def createUnmergedMiroImage = createUnmergedImageWith(
            location = DigitalLocation(
              url = "https://iiif.wellcomecollection.org/V01234.jpg",
              locationType = LocationType("iiif-image"),
              license = Some(License.CCBY)
            )
          )

  def createUnmergedMetsImage = createUnmergedImageWith(
      location = createDigitalLocation,
      identifierType = IdentifierType("mets-image")
    )

  def createMergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number"),
    parentWork: UnidentifiedWork = createUnidentifiedSierraWorkWith(), redirectedWork: Option[TransformedBaseWork] = Some(createMiroWorkWith(Nil))): MergedImage[Identifiable, Unminted] =
    createUnmergedImageWith(location, version, identifierType) mergeWith (
      parentWork.toSourceWork,
      redirectedWork.map(_.toSourceWork)
    )

  def createMergedImage: MergedImage[Identifiable, Unminted] = createMergedImageWith()

  def createIdentifiedMergedImageWith(
                                       imageId: Identified = Identified(createCanonicalId, createSourceIdentifier),
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
                                       parentWork: IdentifiedWork = createIdentifiedSierraWorkWith(),
                                       redirectedWork: Option[IdentifiedWork]=Some(createIdentifiedSierraWorkWith())): MergedImage[Identified, Minted] = MergedImage[Identified, Minted](imageId, version, location , SourceWorks[Identified, Minted](parentWork.toSourceWork, redirectedWork.map(_.toSourceWork)))

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
     parentWork: IdentifiedWork = createIdentifiedSierraWorkWith(),
     redirectedWork: Option[IdentifiedWork] = Some(createIdentifiedWork),
    inferredData: Option[InferredData] = createInferredData,
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
  ) =
    createIdentifiedMergedImageWith(
      imageId,
      location,
      version,parentWork, redirectedWork
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
