package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  DigitalLocation,
  License,
  LocationType,
  PhysicalLocation
}

trait LocationGenerators extends RandomGenerators {
  def createPhysicalLocationWith(
    locationType: LocationType = LocationType("sgmed"),
    accessConditions: List[AccessCondition] = Nil,
    label: String = "locationLabel"
  ): PhysicalLocation =
    PhysicalLocation(
      locationType = locationType,
      label = label,
      accessConditions = accessConditions
    )

  def createPhysicalLocation: PhysicalLocation = createPhysicalLocationWith()

  private def defaultLocationUrl =
    s"https://iiif.wellcomecollection.org/image/${randomAlphanumeric(3)}.jpg/info.json"

  def createDigitalLocationWith(
    locationType: LocationType = LocationType("iiif-presentation"),
    url: String = defaultLocationUrl,
    license: Option[License] = Some(License.CCBY),
    accessConditions: List[AccessCondition] = Nil
  ): DigitalLocation = DigitalLocation(
    locationType = locationType,
    url = url,
    license = license,
    accessConditions = accessConditions
  )

  def createDigitalLocation: DigitalLocation = createDigitalLocationWith()

  def createImageLocation: DigitalLocation =
    createDigitalLocationWith(
      locationType = LocationType("iiif-image")
    )

  def createManifestLocation: DigitalLocation =
    createDigitalLocationWith(
      locationType = LocationType("iiif-presentation")
    )
}
