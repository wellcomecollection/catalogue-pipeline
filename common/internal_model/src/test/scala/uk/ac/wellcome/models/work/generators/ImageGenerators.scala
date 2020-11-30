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
  def createSourceImageWith(
    locations: List[DigitalLocationDeprecated] = List(createDigitalLocation),
    version: Int = 1,
    identifierValue: String = randomAlphanumeric(10),
    identifierType: IdentifierType = IdentifierType("miro-image-number")
  ): Image[Source] =
    Image[Source](
      state = Source(
        sourceIdentifier = createSourceIdentifierWith(
          identifierType = identifierType,
          value = identifierValue
        )
      ),
      version = version,
      locations = locations
    )

  def createSourceImage: Image[Source] = createSourceImageWith()

  def createSourceMiroImage = createSourceImageWith(
    locations = List(
      DigitalLocationDeprecated(
        url = "https://iiif.wellcomecollection.org/V01234.jpg",
        locationType = LocationType("iiif-image"),
        license = Some(License.CCBY)
      ))
  )

  def createSourceMetsImage = createSourceImageWith(
    locations = List(createDigitalLocation),
    identifierType = IdentifierType("mets-image")
  )

  def createIdentifiedImageWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: String = createCanonicalId,
    locations: List[DigitalLocationDeprecated] = List(
      createDigitalLocationWith(locationType = createImageLocationType)),
    version: Int = 1,
    modifiedTime: Instant = instantInLast30Days,
    parentWork: Work.Visible[WorkState.Identified] = sierraIdentifiedWork(),
    redirectedWork: Option[Work[WorkState.Identified]] = Some(
      sierraIdentifiedWork())): Image[Identified] =
    Image[Identified](
      version = version,
      locations = locations,
      state = Identified(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
        modifiedTime = modifiedTime,
        source = SourceWorks(
          parentWork.toSourceWork,
          redirectedWork.map(_.toSourceWork)
        )
      )
    )

  def createIdentifiedImage = createIdentifiedImageWith()

  def createIdentifiedSourceImageWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: String = createCanonicalId,
    locations: List[DigitalLocationDeprecated] = List(
      createDigitalLocationWith(locationType = createImageLocationType)),
    version: Int = 1
  ): Image[IdentifiedSource] =
    Image[IdentifiedSource](
      version = version,
      locations = locations,
      state = IdentifiedSource(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId
      )
    )

  def createIdentifiedSourceImage = createIdentifiedSourceImageWith()

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

  def createAugmentedImageWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifierWith(
      IdentifierType("miro-image-number")),
    canonicalId: String = createCanonicalId,
    parentWork: Work.Visible[WorkState.Identified] = sierraIdentifiedWork(),
    redirectedWork: Option[Work.Visible[WorkState.Identified]] = Some(
      identifiedWork()),
    inferredData: Option[InferredData] = createInferredData,
    locations: List[DigitalLocationDeprecated] = List(createDigitalLocation),
    version: Int = 1,
    modifiedTime: Instant = instantInLast30Days,
  ): Image[Augmented] =
    createIdentifiedImageWith(
      sourceIdentifier,
      canonicalId,
      locations,
      version,
      modifiedTime,
      parentWork,
      redirectedWork
    ).transition[Augmented](inferredData)

  def createAugmentedImage(): Image[Augmented] = createAugmentedImageWith()

  def createLicensedImage(license: License): Image[Augmented] =
    createAugmentedImageWith(
      locations = List(createDigitalLocationWith(license = Some(license)))
    )

  // Create a set of images with intersecting LSH lists to ensure
  // that similarity queries will return something. Returns them in order
  // of similarity.
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
        createAugmentedImageWith(
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
