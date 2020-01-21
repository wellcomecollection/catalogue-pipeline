package uk.ac.wellcome.platform.transformer.mets.transformer

import java.io.File
import java.net.URLConnection

import uk.ac.wellcome.models.work.internal._
import cats.implicits._
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase

case class MetsData(
  recordIdentifier: String,
  accessConditionDz: Option[String] = None,
  accessConditionStatus: Option[String] = None,
  accessConditionUsage: Option[String] = None,
  thumbnailLocation: Option[String] = None
) {

  def toWork(version: Int): Either[Throwable, UnidentifiedInvisibleWork] =
    for {
      maybeLicense <- parseLicense
      accessStatus <- parseAccessStatus
      item = Item(
        id = Unidentifiable,
        locations = List(digitalLocation(maybeLicense, accessStatus)))
    } yield
      UnidentifiedInvisibleWork(
        version = version,
        sourceIdentifier = sourceIdentifier,
        workData(item, thumbnail(maybeLicense, sourceIdentifier.value))
      )

  private def workData(item: Item[Unminted],
                       thumbnail: Option[DigitalLocation]) =
    WorkData(
      items = List(item),
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

  private def digitalLocation(license: Option[License],
                              accessStatus: Option[AccessStatus]) =
    DigitalLocation(
      url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest",
      locationType = LocationType("iiif-presentation"),
      license = license,
      accessConditions = accessStatus.map { status =>
        List(AccessCondition(status = status, terms = accessConditionUsage))
      }
    )

  // The access conditions in mets contains sometimes the license id (lowercase),
  // sometimes the label (ie "in copyright")
  // and sometimes the url of the license
  private def parseLicense: Either[Exception, Option[License]] =
    accessConditionDz.map { accessCondition =>
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

  private val parseAccessStatus: Either[Exception, Option[AccessStatus]] =
    accessConditionStatus
      .map(_.toLowerCase)
      .map {
        case "open"                  => Right(AccessStatus.Open)
        case "requires registration" => Right(AccessStatus.OpenWithAdvisory)
        case "restricted"            => Right(AccessStatus.Restricted)
        case "restricted files"      => Right(AccessStatus.Restricted)
        case "clinical images"       => Right(AccessStatus.Restricted)
        case "closed"                => Right(AccessStatus.Closed)
        case "in copyright"          => Right(AccessStatus.LicensedResources)
        case status =>
          Left(new Exception(s"Unrecognised access status: $status"))
      }
      .sequence

  private def sourceIdentifier =
    SourceIdentifier(
      IdentifierType("mets"),
      ontologyType = "Work",
      value = recordIdentifier)

  private val thumbnailDim = "200"

  private def thumbnail(maybeLicense: Option[License], bnumber: String) =
    for {
      location <- thumbnailLocation
      url <- buildThumbnailUrl(location, bnumber)
    } yield
      DigitalLocation(
        url = url,
        locationType = LocationType("thumbnail-image"),
        license = maybeLicense
      )

  private def buildThumbnailUrl(location: String, bnumber: String) = {
    val mediaType =
      URLConnection.guessContentTypeFromName(new File(location).getName)
    Option(mediaType) match {
      case Some(m) if m startsWith ("video/") => None
      case Some(m) if m equals ("application/pdf") =>
        Some(
          s"https://wellcomelibrary.org/pdfthumbs/${bnumber}/0/${location}.jpg")
      case _ =>
        Some(
          s"https://dlcs.io/thumbs/wellcome/5/$location/full/!$thumbnailDim,$thumbnailDim/0/default.jpg")
    }
  }
}
