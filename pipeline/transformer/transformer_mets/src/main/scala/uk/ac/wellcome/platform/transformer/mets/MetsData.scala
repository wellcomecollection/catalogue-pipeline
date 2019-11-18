package uk.ac.wellcome.platform.transformer.mets

import uk.ac.wellcome.models.work.internal._

case class MetsData(
                     recordIdentifier: String,
                     accessCondition: Option[String]
                   ) {

  def toWork(version: Int): UnidentifiedInvisibleWork = {
    val expectedSourceIdentifier = SourceIdentifier(IdentifierType("mets", "mets"), ontologyType = "Work", value = recordIdentifier)

    val url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest"

    val maybeLicense = accessCondition.map { license => License.createLicense(license.toLowerCase) }
    val digitalLocation = DigitalLocation(url, LocationType("iiif-presentation"), license = maybeLicense)
    val unidentifiableItem: MaybeDisplayable[Item] = Unidentifiable(Item(locations = List(digitalLocation)))
    UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = expectedSourceIdentifier,
      WorkData(items = List(unidentifiableItem))
    )
  }
}
