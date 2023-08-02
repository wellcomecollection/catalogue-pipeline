package weco.pipeline.ingestor.images

import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.image.{Image, ImageState, ParentWork}
import weco.pipeline.ingestor.images.models.{
  DebugInformation,
  ImageAggregatableValues,
  ImageQueryableValues,
  IndexedImage
}
import weco.catalogue.display_model.Implicits._
import io.circe.syntax._
import weco.catalogue.internal_model.locations.DigitalLocation

import java.time.Instant

trait ImageTransformer {
  // For the purpose of discovery/search/faceting, the record
  // for an image should contain *all* of the licences that may
  // apply to it, either directly or indirectly via its parent Work.
  // The display portion of the record is faithful
  // to the source data in this regard, only showing the licences
  // (or rather, locations that apply directly to it).

  // As a result, a search for images with a certain licence may
  // yield records whose licence as displayed differs from that
  // request,

  // This appears to be the best approach.
  // * Representing only the Image's licences is inappropriate
  // because some MIRO images have a default "CC-BY" that is
  // to be overridden by the Work's licence.
  // * Representing only the Work's licences is inappropriate
  // because some Works do not have a licence defined, and the
  // Image's licence should stand.
  val deriveData: Image[ImageState.Augmented] => IndexedImage =
    image =>
      IndexedImage(
        modifiedTime = image.modifiedTime,
        display = DisplayImage(image).asJson.deepDropNullValues,
        query = ImageQueryableValues(
          id = image.state.canonicalId,
          sourceIdentifier = image.state.sourceIdentifier,
          locations = allLocations(image),
          inferredData = image.state.inferredData,
          source = image.source
        ),
        aggregatableValues = ImageAggregatableValues(image),
        debug = DebugInformation(indexedTime = getIndexedTime)
      )

  // This is a def rather than an inline call so we can override it in the
  // tests; in particular we want it to be deterministic when we're creating
  // example documents to send to the API repo.
  protected def getIndexedTime: Instant = Instant.now()

  private def allLocations(
    image: Image[ImageState.Augmented]
  ): List[DigitalLocation] = {
    val sourceLocations: Seq[DigitalLocation] = image.source match {
      case ParentWork(_, workData, _) =>
        workData.items.flatMap(_.locations) collect {
          case digitalLocation: DigitalLocation => digitalLocation
        }
    }
    image.locations ++ sourceLocations
  }
}

object ImageTransformer extends ImageTransformer
