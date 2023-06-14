package weco.catalogue.internal_model.generators

//import weco.catalogue.internal_model.generators.VectorOps.normalize
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
      )
    )
  )

  def createMetsImageData = createImageDataWith(
    locations = List(createImageLocation, createManifestLocation),
    identifierType = IdentifierType.METSImage
  )

  implicit class IdentifiableImageDataOps(
    imageData: ImageData[IdState.Identifiable]
  ) {

    def toAugmentedImageWith(
      inferredData: InferredData = createInferredData,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork()
      )
    ): Image[ImageState.Augmented] =
      imageData.toIdentified
        .toAugmentedImageWith(
          inferredData = inferredData,
          parentWork = parentWork,
          redirectedWork = redirectedWork
        )

    def toIdentifiedWith(
      canonicalId: CanonicalId = createCanonicalId
    ): ImageData[IdState.Identified] = {

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
  }

  implicit class IdentifiedImageDataOps(
    imageData: ImageData[IdState.Identified]
  ) {
    def toInitialImageWith(
      modifiedTime: Instant = randomInstant,
      parentWork: ParentWork = mergedWork().toParentWork
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
      inferredData: InferredData = createInferredData,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork()
      )
    ): Image[ImageState.Augmented] =
      imageData
        .toInitialImageWith(parentWork = parentWork.toParentWork)
        .transition[ImageState.Augmented](inferredData)

    def toInitialImage = toInitialImageWith()

    def toAugmentedImage = toAugmentedImageWith()
  }

  lazy private val inferredDataAspectRatio = Some(random.nextFloat())

  def randomHexString: String =
    s"#${randomBytes(3).map(b => f"$b%02X").mkString}"

  def createInferredData: InferredData = {
    val features = randomVector(4096)
    val (features1, features2) = features.splitAt(features.size / 2)
    val reducedFeatures = randomVector(1024)
    val paletteEmbedding = randomVector(216)
    InferredData(
      features1 = features1.toList,
      features2 = features2.toList,
      reducedFeatures = reducedFeatures.toList,
      paletteEmbedding = paletteEmbedding.toList,
      averageColorHex = Some(randomHexString),
      aspectRatio = inferredDataAspectRatio
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
}
