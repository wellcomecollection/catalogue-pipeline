package uk.ac.wellcome.platform.transformer.mets.transformer

import uk.ac.wellcome.models.work.internal._

import cats.implicits._
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase

case class MetsData(
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

  private def workData(unidentifiableItem: MaybeDisplayable[Item],
                       thumbnail: Option[DigitalLocation]) =
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

  // The access conditions in mets contains sometimes the license id (lowercase),
  // sometimes the label (ie "in copyright")
  // and sometimes the url of the license
  private def parseLicense = {
    accessCondition.map { accessCondition =>
      License.values.find { license =>
        equalsIgnoreCase(license.id, accessCondition) || equalsIgnoreCase(
          license.label,
          accessCondition) || license.url.equals(accessCondition)
      } match {
        case Some(license) => Right(license)
        case None =>
          Left(new Exception(s"Couldn't match $accessCondition to a license"))
      }
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
        url =
          s"https://dlcs.io/iiif-img/wellcome/5/$location/full/!$thumbnailDim,$thumbnailDim/0/default.jpg",
        locationType = LocationType("thumbnail-image"),
        license = maybeLicense
      )
    }
}
