package uk.ac.wellcome.models.work.generators

import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.{IdState, SourceIdentifier}
import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}
import weco.catalogue.internal_model.work.{Item, Work, WorkState}

import scala.util.Random

trait MiroWorkGenerators extends ImageGenerators {
  def miroThumbnail() =
    DigitalLocation(
      url =
        s"https://iiif.wellcomecollection.org/${randomAlphanumeric(length = 8)}.jpg",
      locationType = LocationType.ThumbnailImage,
      license = Some(License.CCBY)
    )

  def miroItems(
    count: Int = Random.nextInt(5)): List[Item[IdState.Unidentifiable.type]] =
    (1 to count).map { _ =>
      createUnidentifiableItemWith(
        locations = List(createImageLocation)
      )
    }.toList

  def miroIdentifiedWork(
    sourceIdentifier: SourceIdentifier = createMiroSourceIdentifier)
    : Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = sourceIdentifier)
      .thumbnail(miroThumbnail())
      .items(miroItems(count = 1))
      .imageData(List(createMiroImageData.toIdentified))
}
