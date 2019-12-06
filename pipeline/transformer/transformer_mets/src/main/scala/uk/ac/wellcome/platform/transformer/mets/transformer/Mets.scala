package uk.ac.wellcome.platform.transformer.mets.transformer

import uk.ac.wellcome.models.work.internal._

import scala.util.Try
import cats.implicits._

case class Mets(
  recordIdentifier: String,
  accessCondition: Option[String],
  thumbnailLocation: Option[String] = None
) {

  def toWork(version: Int): Either[Throwable, UnidentifiedInvisibleWork] =
    for {
      maybeLicense <- parseLicense
      unidentifiableItem: MaybeDisplayable[Item] = Unidentifiable(
        Item(locations = List(digitalLocation(maybeLicense))))
    } yield
      UnidentifiedInvisibleWork(
        version = version,
        sourceIdentifier = sourceIdentifier,
        workData(unidentifiableItem, thumbnail(maybeLicense))
      )

  private def workData(unidentifiableItem: MaybeDisplayable[Item], thumbnail: Option[DigitalLocation]) =
    WorkData(
      items = List(unidentifiableItem),
      mergeCandidates = List(mergeCandidate),
      thumbnail = thumbnail,
    )

  private def mergeCandidate = MergeCandidate(
    identifier = SourceIdentifier(
      identifierType = IdentifierType("sierra-system-number"),
      ontologyType = "Work",
      value = recordIdentifier
    ),
    reason = Some("METS work")
  )

  private def digitalLocation(maybeLicense: Option[License]) = {
    val url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest"
      DigitalLocation(
        url,
        LocationType("iiif-presentation"),
        license = maybeLicense)
  }

  private def parseLicense = {
    accessCondition.map { license =>
      Try(License.createLicense(license.toLowerCase)).toEither
    }.sequence
  }

  private def sourceIdentifier = {
    SourceIdentifier(
      IdentifierType("mets"),
      ontologyType = "Work",
      value = recordIdentifier)
  }

  private val thumbnailDim = "200"

  private def thumbnail(maybeLicense: Option[License]) =
    thumbnailLocation.map { location =>
      DigitalLocation(
        url = s"https://dlcs.io/iiif-img/wellcome/5/$location/full/!$thumbnailDim,$thumbnailDim/0/default.jpg",
        locationType = LocationType("thumbnail-image"),
        license = maybeLicense
      )
    }
}
