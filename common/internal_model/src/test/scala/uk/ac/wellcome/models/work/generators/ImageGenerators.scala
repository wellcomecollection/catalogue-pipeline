package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait ImageGenerators
    extends IdentifiersGenerators
    with ItemsGenerators
    with WorksGenerators
    with VectorGenerators {
  def createUnmergedImageWith(
    location: DigitalLocationDeprecated = createDigitalLocation,
    version: Int = 1,
    identifierValue: String = randomAlphanumeric(10),
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): UnmergedImage[IdState.Identifiable, IdState.Unminted] = UnmergedImage(
    sourceIdentifier = createSourceIdentifierWith(
      identifierType = identifierType,
      value = identifierValue),
    version = version,
    location = location
  )

  def createUnmergedImage: UnmergedImage[IdState.Identifiable, IdState.Unminted] =
    createUnmergedImageWith()

  def createUnmergedMiroImage = createUnmergedImageWith(
    location = DigitalLocationDeprecated(
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
    location: DigitalLocationDeprecated = createDigitalLocation,
    version: Int = 1,
    identifierType: IdentifierType = IdentifierType("miro-image-number"),
    parentWork: UnidentifiedWork = createUnidentifiedSierraWorkWith(),
    redirectedWork: Option[TransformedBaseWork] = Some(createMiroWorkWith(Nil)))
    : MergedImage[IdState.Identifiable, IdState.Unminted] =
    createUnmergedImageWith(location, version, identifierType = identifierType) mergeWith (
      parentWork.toSourceWork,
      redirectedWork.map(_.toSourceWork)
    )

  def createMergedImage: MergedImage[IdState.Identifiable, IdState.Unminted] =
    createMergedImageWith()

  def createIdentifiedMergedImageWith(
    imageId: IdState.Identified = IdState.Identified(createCanonicalId, createSourceIdentifier),
    location: DigitalLocationDeprecated = createDigitalLocation,
    version: Int = 1,
    parentWork: IdentifiedWork = createIdentifiedSierraWorkWith(),
    redirectedWork: Option[IdentifiedWork] = Some(
      createIdentifiedSierraWorkWith())): MergedImage[IdState.Identified, IdState.Minted] =
    MergedImage[IdState.Identified, IdState.Minted](
      imageId,
      version,
      location,
      SourceWorks[IdState.Identified, IdState.Minted](
        parentWork.toSourceWork,
        redirectedWork.map(_.toSourceWork)))

  def createInferredData = {
    val features = randomVector(4096)
    val (features1, features2) = features.splitAt(features.size / 2)
    val lshEncodedFeatures = simHasher4096.lsh(features)
    val palette = randomSortedIntegerVector(20, maxComponent = 1000)
    Some(
      InferredData(
        features1 = features1.toList,
        features2 = features2.toList,
        lshEncodedFeatures = lshEncodedFeatures.toList,
        palette = palette.map(_.toString).toList
      )
    )
  }

  def createAugmentedImageWith(
    imageId: IdState.Identified = IdState.Identified(
      createCanonicalId,
      createSourceIdentifierWith(IdentifierType("miro-image-number"))),
    parentWork: IdentifiedWork = createIdentifiedSierraWorkWith(),
    redirectedWork: Option[IdentifiedWork] = Some(createIdentifiedWork),
    inferredData: Option[InferredData] = createInferredData,
    location: DigitalLocationDeprecated = createDigitalLocation,
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
    val features = if (similarFeatures) {
      similarVectors(4096, n)
    } else { (1 to n).map(_ => randomVector(4096, maxR = 10.0f)) }
    val palettes = if (similarPalette) {
      similarSortedIntegerVectors(30, n)
    } else {
      (1 to n).map(_ => randomSortedIntegerVector(30, maxComponent = 1000))
    }
    (features zip palettes).map {
      case (features, palette) =>
        createAugmentedImageWith(
          inferredData = Some(
            InferredData(
              features1 = features.slice(0, 2048).toList,
              features2 = features.slice(2048, 4096).toList,
              lshEncodedFeatures = simHasher4096.lsh(features).toList,
              palette = palette.map(_.toString).toList
            )
          )
        )
    }
  }

  implicit class UnmergedImageIdOps(
    val image: UnmergedImage[IdState.Identifiable, IdState.Unminted]) {
    def toIdentifiedWith(
      id: String = createCanonicalId): UnmergedImage[IdState.Identified, IdState.Minted] =
      UnmergedImage(
        id = IdState.Identified(
          canonicalId = id,
          sourceIdentifier = image.id.allSourceIdentifiers.head
        ),
        version = image.version,
        location = image.location
      )

    val toIdentified: UnmergedImage[IdState.Identified, IdState.Minted] = toIdentifiedWith()
  }
}
