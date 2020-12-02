package uk.ac.wellcome.models.work.generators

import java.time.Instant

import uk.ac.wellcome.models.work.internal._
import SourceWork._
import ImageState._

trait ImageGenerators
    extends IdentifiersGenerators
    with ItemsGenerators
    with InstantGenerators
    with VectorGenerators
    with SierraWorkGenerators {
  def createImageDataWith(
    locations: List[DigitalLocationDeprecated] = List(createImageLocation),
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
      DigitalLocationDeprecated(
        url = "https://iiif.wellcomecollection.org/V01234.jpg",
        locationType = LocationType("iiif-image"),
        license = Some(License.CCBY)
      ))
  )

  def createMetsImageData = createImageDataWith(
    locations = List(createDigitalLocation),
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

    def toMergedImageWith(
      modifiedTime: Instant = instantInLast30Days,
      sourceWorks: SourceWorks[DataState.Unidentified] = SourceWorks(
        canonicalWork = mergedWork().toSourceWork,
        redirectedWork = None
      )
    ): Image[ImageState.Merged] = Image(
      version = imageData.version,
      locations = imageData.locations,
      state = ImageState.Merged(
        sourceIdentifier = imageData.id.sourceIdentifier,
        modifiedTime = modifiedTime,
        source = sourceWorks
      )
    )

    def toIdentifiedImageWith(
      canonicalId: String = createCanonicalId,
      modifiedTime: Instant = instantInLast30Days,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())
    ): Image[ImageState.Identified] = Image(
      version = imageData.version,
      locations = imageData.locations,
      state = ImageState.Identified(
        sourceIdentifier = imageData.id.sourceIdentifier,
        canonicalId = canonicalId,
        modifiedTime = modifiedTime,
        source = SourceWorks(
          parentWork.toSourceWork,
          redirectedWork.map(_.toSourceWork)
        )
      )
    )

    def toAugmentedImageWith(
      inferredData: Option[InferredData] = createInferredData,
      canonicalId: String = createCanonicalId,
      modifiedTime: Instant = instantInLast30Days,
      parentWork: Work[WorkState.Identified] = sierraIdentifiedWork(),
      redirectedWork: Option[Work[WorkState.Identified]] = Some(
        sierraIdentifiedWork())
    ): Image[ImageState.Augmented] =
      imageData
        .toIdentifiedImageWith(
          canonicalId = canonicalId,
          modifiedTime = modifiedTime,
          parentWork = parentWork,
          redirectedWork = redirectedWork
        )
        .transition[ImageState.Augmented](inferredData)

    def toIdentified = toIdentifiedWith()

    def toMergedImage = toMergedImageWith()

    def toIdentifiedImage = toIdentifiedImageWith()

    def toAugmentedImage = toAugmentedImageWith()
  }

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
        palette = palette.toList
      )
    )
  }

  def createLicensedImage(license: License): Image[Augmented] =
    createImageDataWith(
      locations = List(createDigitalLocationWith(license = Some(license)))
    ).toAugmentedImage

//   Create a set of images with intersecting LSH lists to ensure
//   that similarity queries will return something. Returns them in order
//   of similarity.
  def createSimilarImages(n: Int,
                          similarFeatures: Boolean,
                          similarPalette: Boolean): Seq[Image[Augmented]] = {
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
              palette = p.toList
            )
          )
        )
    }
  }
}
