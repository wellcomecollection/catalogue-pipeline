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
      maybeDigitalLocation <- digitalLocation
      unidentifiableItem: MaybeDisplayable[Item] = Unidentifiable(
        Item(locations = List(maybeDigitalLocation)))
    } yield
      UnidentifiedInvisibleWork(
        version = version,
        sourceIdentifier = sourceIdentifier,
        workData(unidentifiableItem)
      )

  private def workData(unidentifiableItem: MaybeDisplayable[Item]) =
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

  private def digitalLocation = {
    val url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest"
    for {
      maybeLicense <- parseLicense
    } yield DigitalLocation(
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

  private val thumbnailHeight = "200"

  private def thumbnail =
    thumbnailLocation.map { location =>
      DigitalLocation(
        s"https://dlcs.io/iiif-img/wellcome/5/$location/full/,$thumbnailHeight/0/default.jpg",
        LocationType("thumbnail-image"),
      )
    }
}
