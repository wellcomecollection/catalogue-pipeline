package uk.ac.wellcome.platform.transformer.mets.transformer

import cats.syntax.traverse._
import cats.instances.either._
import cats.instances.option._
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase
import uk.ac.wellcome.models.work.internal._

case class MetsData(
  recordIdentifier: String,
  accessConditionDz: Option[String] = None,
  accessConditionStatus: Option[String] = None,
  accessConditionUsage: Option[String] = None,
  fileObjects: List[FileObject] = Nil
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
      images = images
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
        AccessCondition(status = status, terms = accessConditionUsage)
      }.toList
    )

  private def parseLicense: Either[Exception, Option[License]] =
    accessConditionDz.map {
      // A lot of METS record have "Copyright not cleared"
      // or "rightsstatements.org/page/InC/1.0/?language=en" as dz access condition.
      // They both need to be mapped to a InCopyright license so hardcoding here
      case s if s.toLowerCase() == "copyright not cleared" =>
        Right(License.InCopyright)
      case s if s == "rightsstatements.org/page/InC/1.0/?language=en" =>
        Right(License.InCopyright)
      case s if s.toLowerCase == "all rights reserved" =>
        Right(License.InCopyright)
      // The access conditions in mets contains sometimes the license id (lowercase),
      // sometimes the label (ie "in copyright")
      // and sometimes the url of the license
      case accessCondition =>
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
      fileObject <- fileObjects.headOption
      url <- buildImageUrl(bnumber, fileObject)
    } yield
      DigitalLocation(
        url = url,
        locationType = LocationType("thumbnail-image"),
        license = maybeLicense
      )

  private val images = fileObjects
    .filter {
      _.mimeType match {
        case Some(m) => m == "application/pdf" || m.startsWith("image")
        case None    => true
      }
    }
    .map { fileObject =>
      UnmergedImage(
        getImageSourceId(recordIdentifier, fileObject.id),
        DigitalLocation(
          url = buildImageUrl(recordIdentifier, fileObject).getOrElse(""),
          locationType = LocationType("iiif-image")
        )
      )
    }

  private def getImageSourceId(bnumber: String,
                               fileId: String): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType("mets-image"),
      ontologyType = "Image",
      value = s"$bnumber/$fileId"
    )

  private def buildImageUrl(bnumber: String, fileObject: FileObject) = {
    fileObject.mimeType match {
      case Some(m) if m startsWith ("video/") => None
      case Some(m) if m equals ("application/pdf") =>
        Some(
          s"https://wellcomelibrary.org/pdfthumbs/${bnumber}/0/${fileObject.href}.jpg")
      case _ =>
        Some(
          s"https://dlcs.io/thumbs/wellcome/5/${fileObject.href}/full/!$thumbnailDim,$thumbnailDim/0/default.jpg")
    }
  }
}
