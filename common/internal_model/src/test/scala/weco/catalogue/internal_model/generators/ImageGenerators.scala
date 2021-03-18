package weco.catalogue.internal_model.generators

import uk.ac.wellcome.models.work.generators._
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.catalogue.internal_model.image
import weco.catalogue.internal_model.image.ParentWork._
import weco.catalogue.internal_model.image.ImageState.{Indexed, Initial}
import weco.catalogue.internal_model.image._
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  License,
  LocationType
}
import weco.catalogue.internal_model.work.{Work, WorkState}

import java.time.Instant
import scala.util.Random

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
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): ImageData[IdState.Identifiable] =
    ImageData[IdState.Identifiable](
      id = IdState.Identifiable(
        sourceIdentifier = createSourceIdentifierWith(
          identifierType = identifierType,
          value = identifierValue
        )
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
    identifierType = IdentifierType("mets-image")
  )

  implicit class UnidentifiedImageDataOps(
    imageData: ImageData[IdState.Identifiable]) {

    def toIdentifiedWith(
      canonicalId: String = createCanonicalId): ImageData[IdState.Identified] =
      imageData.copy(
        id = IdState.Identified(
          canonicalId = canonicalId,
          sourceIdentifier = imageData.id.sourceIdentifier
        )
      )

    def toAugmentedImageWith(
      canonicalId: String = createCanonicalId,
      inferredData: Option[InferredData] = createInferredData,
      modifiedTime: Instant = instantInLast30Days,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())): Image[ImageState.Augmented] =
      imageData
        .toIdentifiedWith(canonicalId)
        .toAugmentedImageWith(
          inferredData,
          modifiedTime,
          parentWork,
          redirectedWork)

    def toIndexedImageWith(
      canonicalId: String = createCanonicalId,
      inferredData: Option[InferredData] = createInferredData,
      modifiedTime: Instant = instantInLast30Days,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())): Image[ImageState.Indexed] =
      imageData
        .toIdentifiedWith(canonicalId)
        .toIndexedImageWith(
          inferredData,
          modifiedTime,
          parentWork,
          redirectedWork)

    def toIdentified = toIdentifiedWith()

    def toInitialImage: Image[Initial] =
      imageData.toIdentified.toInitialImage

    def toAugmentedImage = toAugmentedImageWith()

    def toIndexedImage = toIndexedImageWith()
  }

  implicit class IdentifiedImageDataOps(
    imageData: ImageData[IdState.Identified]) {
    def toInitialImageWith(
      modifiedTime: Instant = instantInLast30Days,
      parentWorks: ParentWorks = ParentWorks(
        canonicalWork = mergedWork().toParentWork,
        redirectedWork = None
      )
    ): Image[ImageState.Initial] = Image[ImageState.Initial](
      version = imageData.version,
      locations = imageData.locations,
      modifiedTime = modifiedTime,
      source = parentWorks,
      state = ImageState.Initial(
        sourceIdentifier = imageData.id.sourceIdentifier,
        canonicalId = imageData.id.canonicalId
      )
    )

    def toAugmentedImageWith(
      inferredData: Option[InferredData] = createInferredData,
      modifiedTime: Instant = instantInLast30Days,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())
    ): Image[ImageState.Augmented] =
      imageData
        .toInitialImageWith(
          modifiedTime = modifiedTime,
          parentWorks = image.ParentWorks(
            canonicalWork = parentWork.toParentWork,
            redirectedWork = redirectedWork.map(_.toParentWork))
        )
        .transition[ImageState.Augmented](inferredData)

    def toIndexedImageWith(
      inferredData: Option[InferredData] = createInferredData,
      modifiedTime: Instant = instantInLast30Days,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())): Image[ImageState.Indexed] =
      imageData
        .toAugmentedImageWith(
          modifiedTime = modifiedTime,
          parentWork = parentWork,
          redirectedWork = redirectedWork,
          inferredData = inferredData
        )
        .transition[ImageState.Indexed]()

    def toInitialImage = toInitialImageWith()

    def toAugmentedImage = toAugmentedImageWith()

    def toIndexedImage = toIndexedImageWith()
  }

  lazy private val inferredDataBinSizes =
    List.fill(9)(Random.nextInt(10)).grouped(3).toList

  lazy private val inferredDataBinMinima = List.fill(3)(Random.nextFloat)

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
        binMinima = inferredDataBinMinima
      )
    )
  }

  def createLicensedImage(license: License): Image[Indexed] =
    createImageDataWith(
      locations = List(
        createDigitalLocationWith(
          license = Some(license),
          locationType = LocationType.IIIFImageAPI))
    ).toIndexedImage

  //   Create a set of images with intersecting LSH lists to ensure
  //   that similarity queries will return something. Returns them in order
  //   of similarity.
  def createSimilarImages(n: Int,
                          similarFeatures: Boolean,
                          similarPalette: Boolean): Seq[Image[Indexed]] = {
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
        createImageData.toIndexedImageWith(
          inferredData = Some(
            InferredData(
              features1 = f.slice(0, 2048).toList,
              features2 = f.slice(2048, 4096).toList,
              lshEncodedFeatures = l.toList,
              palette = p.toList,
              binSizes = inferredDataBinSizes,
              binMinima = inferredDataBinMinima
            )
          )
        )
    }
  }
}
