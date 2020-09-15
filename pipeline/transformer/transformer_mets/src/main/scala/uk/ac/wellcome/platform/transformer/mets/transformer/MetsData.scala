package uk.ac.wellcome.platform.transformer.mets.transformer

import cats.syntax.traverse._
import cats.instances.either._
import cats.instances.option._
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase

import uk.ac.wellcome.models.work.internal._
import WorkState.Unidentified

case class MetsData(
  recordIdentifier: String,
  accessConditionDz: Option[String] = None,
  accessConditionStatus: Option[String] = None,
  accessConditionUsage: Option[String] = None,
  fileReferencesMapping: List[(String, FileReference)] = Nil,
  titlePageId: Option[String] = None
) {

  def toWork(version: Int): Either[Throwable, Work.Invisible[Unidentified]] =
    for {
      license <- parseLicense
      accessStatus <- parseAccessStatus
      item = Item[IdState.Unminted](
        id = IdState.Unidentifiable,
        locations = List(digitalLocation(license, accessStatus)))
    } yield
      Work.Invisible[Unidentified](
        version = version,
        state = Unidentified(sourceIdentifier),
        data = WorkData[Unidentified, IdState.Identifiable](
          items = List(item),
          mergeCandidates = List(mergeCandidate),
          thumbnail = thumbnail(sourceIdentifier.value, license, accessStatus),
          images = images(version, license, accessStatus)
        )
      )

  private lazy val fileReferences: List[FileReference] =
    fileReferencesMapping.map { case (_, fileReference) => fileReference }

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
    DigitalLocationDeprecated(
      url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest",
      locationType = LocationType("iiif-presentation"),
      license = license,
      accessConditions = accessConditions(accessStatus)
    )

  private def accessConditions(
    accessStatus: Option[AccessStatus]): List[AccessCondition] =
    (accessStatus, accessConditionUsage) match {
      case (None, None) => Nil
      case _ =>
        List(
          AccessCondition(status = accessStatus, terms = accessConditionUsage))
    }

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
      .map(AccessStatus(_))
      .sequence

  private def sourceIdentifier =
    SourceIdentifier(
      IdentifierType("mets"),
      ontologyType = "Work",
      value = recordIdentifier)

  private def titlePageFileReference: Option[FileReference] =
    titlePageId
      .flatMap { titleId =>
        fileReferencesMapping.collectFirst {
          case (id, fileReference) if id == titleId => fileReference
        }
      }

  private def thumbnail(
    bnumber: String,
    license: Option[License],
    accessStatus: Option[AccessStatus]): Option[DigitalLocationDeprecated] =
    for {
      fileReference <- titlePageFileReference
        .orElse(fileReferences.find(ImageUtils.isThumbnail))
      url <- ImageUtils.buildThumbnailUrl(bnumber, fileReference)
      if accessStatus.forall(shouldCreateDigitalLocation)
    } yield
      DigitalLocationDeprecated(
        url = url,
        locationType = LocationType("thumbnail-image"),
        license = license
      )

  private def shouldCreateDigitalLocation(accessStatus: AccessStatus) =
    accessStatus match {
      case AccessStatus.Restricted => false
      case AccessStatus.Closed     => false
      case _                       => true
    }

  private def images(version: Int,
                     license: Option[License],
                     accessStatus: Option[AccessStatus])
    : List[UnmergedImage[IdState.Identifiable, WorkState.Unidentified]] =
    if (accessStatus.forall(shouldCreateDigitalLocation)) {
      fileReferences
        .filter(ImageUtils.isImage)
        .flatMap { fileReference =>
          ImageUtils.buildImageUrl(recordIdentifier, fileReference).map { url =>
            UnmergedImage(
              sourceIdentifier = ImageUtils
                .getImageSourceId(recordIdentifier, fileReference.id),
              version = version,
              location = DigitalLocationDeprecated(
                url = url,
                locationType = LocationType("iiif-image"),
                license = license,
                accessConditions = accessConditions(accessStatus)
              )
            )
          }
        }
    } else {
      Nil
    }

}
