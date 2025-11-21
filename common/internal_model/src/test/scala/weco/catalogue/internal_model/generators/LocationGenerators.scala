package weco.catalogue.internal_model.generators

import weco.fixtures.RandomGenerators
import weco.catalogue.internal_model.locations._

trait LocationGenerators extends RandomGenerators {
  def createPhysicalLocationWith(
    locationType: PhysicalLocationType = chooseFrom(
      LocationType.ClosedStores,
      LocationType.OpenShelves
    ),
    accessConditions: List[AccessCondition] = Nil,
    label: String = "locationLabel",
    license: Option[License] = chooseFrom(
      None,
      Some(License.CCBY),
      Some(License.OGL),
      Some(License.PDM)
    ),
    shelfmark: Option[String] = chooseFrom(
      None,
      Some(s"Shelfmark: ${randomAlphanumeric()}")
    )
  ): PhysicalLocation =
    PhysicalLocation(
      locationType = locationType,
      label = label,
      license = license,
      shelfmark = shelfmark,
      accessConditions = accessConditions
    )

  def createPhysicalLocation: PhysicalLocation = createPhysicalLocationWith()

  private def defaultLocationUrl =
    s"https://iiif.wellcomecollection.org/image/${randomAlphanumeric(3)}.jpg/info.json"

  def createDigitalLocationWith(
    locationType: DigitalLocationType = LocationType.IIIFPresentationAPI,
    url: String = defaultLocationUrl,
    license: Option[License] = Some(License.CCBY),
    accessConditions: List[AccessCondition] = Nil,
    createdDate: Option[String] = None
  ): DigitalLocation = DigitalLocation(
    locationType = locationType,
    url = url,
    license = license,
    credit = chooseFrom(None, Some(s"Credit line: ${randomAlphanumeric()}")),
    linkText = chooseFrom(None, Some(s"Link text: ${randomAlphanumeric()}")),
    accessConditions = accessConditions,
    createdDate = createdDate
  )

  def createDigitalLocation: DigitalLocation = createDigitalLocationWith()

  def createImageLocation: DigitalLocation =
    createDigitalLocationWith(
      locationType = LocationType.IIIFImageAPI
    )

  def createManifestLocation: DigitalLocation =
    createDigitalLocationWith(
      locationType = LocationType.IIIFPresentationAPI
    )
}
