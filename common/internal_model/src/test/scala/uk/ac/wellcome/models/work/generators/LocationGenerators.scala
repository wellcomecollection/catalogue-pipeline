package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  DigitalLocation,
  License,
  OldLocationType,
  PhysicalLocation
}

trait LocationGenerators extends RandomGenerators {
  def createPhysicalLocationWith(
    locationType: OldLocationType = OldLocationType("sgmed"),
    accessConditions: List[AccessCondition] = Nil,
    label: String = "locationLabel"
  ): PhysicalLocation =
    PhysicalLocation(
      locationType = locationType,
      label = label,
      license = chooseFrom(
        None,
        Some(License.CCBY),
        Some(License.OGL),
        Some(License.PDM)
      ),
      shelfmark = chooseFrom(None, Some(s"Shelfmark: ${randomAlphanumeric()}")),
      accessConditions = accessConditions
    )

  def createPhysicalLocation: PhysicalLocation = createPhysicalLocationWith()

  private def defaultLocationUrl =
    s"https://iiif.wellcomecollection.org/image/${randomAlphanumeric(3)}.jpg/info.json"

  def createDigitalLocationWith(
    locationType: OldLocationType = OldLocationType("iiif-presentation"),
    url: String = defaultLocationUrl,
    license: Option[License] = Some(License.CCBY),
    accessConditions: List[AccessCondition] = Nil
  ): DigitalLocation = DigitalLocation(
    locationType = locationType,
    url = url,
    license = license,
    credit = chooseFrom(None, Some(s"Credit line: ${randomAlphanumeric()}")),
    linkText = chooseFrom(None, Some(s"Link text: ${randomAlphanumeric()}")),
    accessConditions = accessConditions
  )

  def createDigitalLocation: DigitalLocation = createDigitalLocationWith()

  def createImageLocation: DigitalLocation =
    createDigitalLocationWith(
      locationType = OldLocationType("iiif-image")
    )

  def createManifestLocation: DigitalLocation =
    createDigitalLocationWith(
      locationType = OldLocationType("iiif-presentation")
    )
}
