package uk.ac.wellcome.platform.transformer.mets

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.mets.exceptions.ShouldNotTransformException

import scala.util.Try

case class MetsData(
                     recordIdentifier: String,
                     accessCondition: Option[String]
                   ) {

  def toWork(version: Int): UnidentifiedInvisibleWork = {
    val unidentifiableItem: MaybeDisplayable[Item] = Unidentifiable(Item(locations = List(digitalLocation)))
    UnidentifiedInvisibleWork(
      version = version,
      sourceIdentifier = sourceIdentifier,
      WorkData(items = List(unidentifiableItem))
    )
  }

  private def digitalLocation = {
    val url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest"

    val digitalLocation = DigitalLocation(url, LocationType("iiif-presentation"), license = parseLicense)
    digitalLocation
  }

  private def parseLicense = {
    accessCondition.map { license =>
      Try(License.createLicense(license.toLowerCase))
        .getOrElse(throw new ShouldNotTransformException(s"$license is not a valid license"))
    }
  }

  private def sourceIdentifier = {
    SourceIdentifier(IdentifierType("mets"), ontologyType = "Work", value = recordIdentifier)
  }
}
