package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

import scala.util.Random

trait MiroWorkGenerators extends ImageGenerators {
  def miroThumbnail() =
    DigitalLocationDeprecated(
      url = s"https://iiif.wellcomecollection.org/${randomAlphanumeric(length = 8)}.jpg",
      locationType = LocationType("thumbnail-image"),
      license = Some(License.CCBY)
    )

  def miroItems(count: Int = Random.nextInt(5)): List[Item[IdState.Unidentifiable.type]] =
    (1 to count).map { _ =>
      createUnidentifiableItemWith(
        locations = List(
          createDigitalLocationWith(locationType = createImageLocationType)
        )
      )
    }.toList

  def miroSourceWork(sourceIdentifier: SourceIdentifier = createMiroSourceIdentifier): Work.Visible[WorkState.Source] =
    sourceWork(sourceIdentifier = sourceIdentifier)
      .thumbnail(miroThumbnail())
      .items(miroItems(count = 1))
      .images(List(createUnmergedMiroImage))
}
