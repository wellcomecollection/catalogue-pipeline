package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait ImageGenerators
    extends IdentifiersGenerators
    with ItemsGenerators
    with WorksGenerators
    with VectorGenerators {
  def createUnmergedImageWith(
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    identifierValue: String = randomAlphanumeric(10),
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): UnmergedImage[Identifiable, Unminted] = UnmergedImage(
    sourceIdentifier = createSourceIdentifierWith(
      identifierType = identifierType,
      value = identifierValue),
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
    parentWork: UnidentifiedWork = createUnidentifiedSierraWorkWith(),
    redirectedWork: Option[TransformedBaseWork] = Some(createMiroWorkWith(Nil)))
    : MergedImage[Identifiable, Unminted] =
    createUnmergedImageWith(location, version, identifierType = identifierType) mergeWith (
      parentWork.toSourceWork,
      redirectedWork.map(_.toSourceWork)
    )

  def createMergedImage: MergedImage[Identifiable, Unminted] =
    createMergedImageWith()

  def createIdentifiedMergedImageWith(
    imageId: Identified = Identified(createCanonicalId, createSourceIdentifier),
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
    parentWork: IdentifiedWork = createIdentifiedSierraWorkWith(),
    redirectedWork: Option[IdentifiedWork] = Some(
      createIdentifiedSierraWorkWith())): MergedImage[Identified, Minted] =
    MergedImage[Identified, Minted](
      imageId,
      version,
      location,
      SourceWorks[Identified, Minted](
        parentWork.toSourceWork,
        redirectedWork.map(_.toSourceWork)))

  def createInferredData = {
    val features = randomVector(4096)
    val (features1, features2) = features.splitAt(features.size / 2)
    val lshEncodedFeatures = simHasher4096.lsh(features)
    val palette = simHasher512.lsh(randomVector(512))
    Some(
      InferredData(
        features1 = features1.toList,
        features2 = features2.toList,
        lshEncodedFeatures = lshEncodedFeatures.toList,
        palette = palette.toList
      )
    )
  }

  def createAugmentedImageWith(
    imageId: Identified = Identified(
      createCanonicalId,
      createSourceIdentifierWith(IdentifierType("miro-image-number"))),
    parentWork: IdentifiedWork = createIdentifiedSierraWorkWith(),
    redirectedWork: Option[IdentifiedWork] = Some(createIdentifiedWork),
    inferredData: Option[InferredData] = createInferredData,
    location: DigitalLocation = createDigitalLocation,
    version: Int = 1,
  ) =
    createIdentifiedMergedImageWith(
      imageId,
      location,
      version,
      parentWork,
      redirectedWork
    ).augment(inferredData)

  def createAugmentedImage(): AugmentedImage = createAugmentedImageWith()

  def createLicensedImage(license: License): AugmentedImage =
    createAugmentedImageWith(
      location = createDigitalLocationWith(license = Some(license))
    )

  // Create a set of images with intersecting LSH lists to ensure
  // that similarity queries will return something. Returns them in order
  // of similarity.
  def createSimilarImages(n: Int,
                          similarFeatures: Boolean,
                          similarPalette: Boolean): Seq[AugmentedImage] = {
    val baseFeaturesAndPalette = (randomVector(4096), randomVector(512))
    val similarFeaturesAndPalette = (1 until n).map { n =>
      val features = if (similarFeatures) {
        subspaceSimilarVector(
          baseFeaturesAndPalette._1,
          similarity = 1f - (n * 0.03f),
          subspaces = 256)
      } else { randomVector(4096) }
      val palette = if (similarPalette) {
        subspaceSimilarVector(
          baseFeaturesAndPalette._2,
          similarity = 1f - (n * 0.03f),
          subspaces = 32)
      } else { randomVector(512) }
      (features, palette)
    }
    (baseFeaturesAndPalette +: similarFeaturesAndPalette).map {
      case (features, palette) =>
        createAugmentedImageWith(
          inferredData = Some(
            InferredData(
              features1 = features.slice(0, 2048).toList,
              features2 = features.slice(2048, 4096).toList,
              lshEncodedFeatures = simHasher4096.lsh(features).toList,
              palette = simHasher512.lsh(palette).toList
            )
          )
        )
    }
  }

  implicit class UnmergedImageIdOps(
    val image: UnmergedImage[Identifiable, Unminted]) {
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
