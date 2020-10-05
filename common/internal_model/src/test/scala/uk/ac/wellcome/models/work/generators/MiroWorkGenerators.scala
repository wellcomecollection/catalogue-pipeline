package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

import scala.util.Random

trait MiroWorkGenerators extends ItemsGenerators {
  def miroThumbnail() =
    DigitalLocationDeprecated(
      url = s"https://iiif.wellcomecollection.org/${randomAlphanumeric(length = 8)}.jpg",
      locationType = LocationType("thumbnail-image"),
      license = Some(License.CCBY)
    )

  def miroItems(count: Int = Random.nextInt(5)): List[Item[IdState.Unidentifiable.type]] =
    (0 to count).map { _ =>
      createUnidentifiableItemWith(
        locations = List(
          createDigitalLocationWith(locationType = createImageLocationType)
        )
      )
    }.toList
}
