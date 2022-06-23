package weco.catalogue.internal_model.generators

import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ParentWork._
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.image._
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  License,
  LocationType
}
import weco.catalogue.internal_model.work.generators.{
  InstantGenerators,
  SierraWorkGenerators
}
import weco.catalogue.internal_model.work.{Work, WorkState}

import java.time.Instant

trait ImageGenerators
    extends IdentifiersGenerators
    with LocationGenerators
    with InstantGenerators
    with VectorGenerators
    with SierraWorkGenerators {

  def createImageDataWith(
    locations: List[DigitalLocation] = List(createImageLocation),
    version: Int = 1,
    identifierValue: String = randomAlphanumeric(10),
    identifierType: IdentifierType = IdentifierType.MiroImageNumber,
    otherIdentifiers: List[SourceIdentifier] = List()
  ): ImageData[IdState.Identifiable] =
    ImageData[IdState.Identifiable](
      id = IdState.Identifiable(
        sourceIdentifier = createSourceIdentifierWith(
          identifierType = identifierType,
          value = identifierValue
        ),
        otherIdentifiers = otherIdentifiers
      ),
      version = version,
      locations = locations
    )

  def createImageData: ImageData[IdState.Identifiable] =
    createImageDataWith()

  def createMiroImageData = createImageDataWith(
    locations = List(
      DigitalLocation(
        url = "https://iiif.wellcomecollection.org/V01234.jpg",
        locationType = LocationType.IIIFImageAPI,
        license = Some(License.CCBY)
      ))
  )

  def createMetsImageData = createImageDataWith(
    locations = List(createImageLocation, createManifestLocation),
    identifierType = IdentifierType.METSImage
  )

  implicit class IdentifiableImageDataOps(
    imageData: ImageData[IdState.Identifiable]) {

    def toAugmentedImageWith(
      inferredData: Option[InferredData] = createInferredData,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())): Image[ImageState.Augmented] =
      imageData.toIdentified
        .toAugmentedImageWith(
          inferredData = inferredData,
          parentWork = parentWork,
          redirectedWork = redirectedWork)

    def toIndexedImageWith(
      canonicalId: CanonicalId = createCanonicalId,
      parentWork: Work[WorkState.Identified] = identifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = None,
      inferredData: Option[InferredData] = createInferredData)
      : Image[ImageState.Indexed] =
      imageData
        .toIdentifiedWith(canonicalId = canonicalId)
        .toIndexedImageWith(
          parentWork = parentWork,
          redirectedWork = redirectedWork,
          inferredData = inferredData
        )

    def toIdentifiedWith(canonicalId: CanonicalId = createCanonicalId)
      : ImageData[IdState.Identified] = {

      // This is for CreateTestWorkDocuments in the ingestors; they have a
      // seeded random instance to ensure deterministic results.
      //
      // I removed a call to createCanonicalId in commit b8efb05 (because
      // we were discarding the argument passed by the caller), but we want
      // to call it anyway to preserve the random state.
      //
      // This avoids a bunch of unnecessary churn in the test documents,
      // which in turn break the downstream API tests.
      val _ = createCanonicalId

      imageData.copy(
        id = IdState.Identified(
          canonicalId = canonicalId,
          sourceIdentifier = imageData.id.sourceIdentifier,
          otherIdentifiers = imageData.id.otherIdentifiers
        )
      )
    }

    def toIdentified: ImageData[IdState.Identified] =
      imageData.toIdentifiedWith()

    def toInitialImage: Image[Initial] =
      imageData.toIdentified.toInitialImage

    def toAugmentedImage = toAugmentedImageWith()

    def toIndexedImage = toIndexedImageWith()
  }

  implicit class IdentifiedImageDataOps(
    imageData: ImageData[IdState.Identified]) {
    def toInitialImageWith(
      modifiedTime: Instant = randomInstant,
      parentWork: ParentWork = mergedWork().toParentWork,
    ): Image[ImageState.Initial] = Image[ImageState.Initial](
      version = imageData.version,
      locations = imageData.locations,
      modifiedTime = modifiedTime,
      source = parentWork,
      state = ImageState.Initial(
        sourceIdentifier = imageData.id.sourceIdentifier,
        canonicalId = imageData.id.canonicalId
      )
    )

    def toAugmentedImageWith(
      inferredData: Option[InferredData] = createInferredData,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())
    ): Image[ImageState.Augmented] =
      imageData
        .toInitialImageWith(parentWork = parentWork.toParentWork)
        .transition[ImageState.Augmented](inferredData)

    def toIndexedImageWith(
      parentWork: Work[WorkState.Identified] = identifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = None,
      inferredData: Option[InferredData] = createInferredData)
      : Image[ImageState.Indexed] =
      imageData
        .toAugmentedImageWith(
          parentWork = parentWork,
          redirectedWork = redirectedWork,
          inferredData = inferredData
        )
        .transition[ImageState.Indexed]()

    def toInitialImage = toInitialImageWith()

    def toAugmentedImage = toAugmentedImageWith()
  }

  lazy private val inferredDataBinSizes =
    List.fill(9)(random.nextInt(10)).grouped(3).toList

  lazy private val inferredDataBinMinima = List.fill(3)(random.nextFloat)

  lazy private val inferredDataAspectRatio = Some(random.nextFloat())

  def createInferredData = {
    val features = randomVector(4096)
    val (features1, features2) = features.splitAt(features.size / 2)
    val lshEncodedFeatures = randomHash(32)
    val palette = randomColorVector()
    Some(
      InferredData(
        features1 = features1.toList,
        features2 = features2.toList,
        lshEncodedFeatures = lshEncodedFeatures.toList,
        palette = palette.toList,
        binSizes = inferredDataBinSizes,
        binMinima = inferredDataBinMinima,
        aspectRatio = inferredDataAspectRatio
      )
    )
  }

  def createLicensedImage(license: License): Image[ImageState.Augmented] = {
    val location = createDigitalLocationWith(
      license = Some(license),
      locationType = LocationType.IIIFImageAPI
    )

    createImageDataWith(locations = List(location))
      .toAugmentedImageWith(
        parentWork = sierraIdentifiedWork()
          .items(
            List(createDigitalItemWith(locations = List(location)))
          )
      )
  }

  //   Create a set of images with intersecting LSH lists to ensure
  //   that similarity queries will return something. Returns them in order
  //   of similarity.
  def createSimilarImages(
    n: Int,
    similarFeatures: Boolean,
    similarPalette: Boolean): Seq[Image[ImageState.Augmented]] = {
    val features = if (similarFeatures) {
      similarVectors(4096, n)
    } else { (1 to n).map(_ => randomVector(4096, maxR = 10.0f)) }
    val lshFeatures = if (similarFeatures) {
      similarHashes(32, n)
    } else {
      (1 to n).map(_ => randomHash(32))
    }
    val palettes = if (similarPalette) {
      similarColorVectors(n)
    } else {
      (1 to n).map(_ => randomColorVector())
    }
    (features, lshFeatures, palettes).zipped.map {
      case (f, l, p) =>
        createImageData.toAugmentedImageWith(
          inferredData = Some(
            InferredData(
              features1 = f.slice(0, 2048).toList,
              features2 = f.slice(2048, 4096).toList,
              lshEncodedFeatures = l.toList,
              palette = p.toList,
              binSizes = inferredDataBinSizes,
              binMinima = inferredDataBinMinima,
              aspectRatio = inferredDataAspectRatio
            )
          )
        )
    }
  }
}
