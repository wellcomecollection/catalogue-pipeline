package uk.ac.wellcome.platform.transformer.mets.transformer

import java.time.Instant
import cats.syntax.traverse._
import cats.instances.either._
import cats.instances.option._
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase
import uk.ac.wellcome.models.work.internal._
import WorkState.Source
import uk.ac.wellcome.models.work.internal.DeletedReason.DeletedFromSource
import uk.ac.wellcome.models.work.internal.InvisibilityReason.MetsWorksAreNotVisible

case class MetsData(
  recordIdentifier: String,
  accessConditionDz: Option[String] = None,
  accessConditionStatus: Option[String] = None,
  accessConditionUsage: Option[String] = None,
  fileReferencesMapping: List[(String, FileReference)] = Nil,
  titlePageId: Option[String] = None,
  deleted: Boolean = false
) {

  def toWork(version: Int,
             modifiedTime: Instant): Either[Throwable, Work[Source]] = {
    deleted match {
      case true =>
        Right(
          Work.Deleted[Source](
            version = version,
            data = WorkData[DataState.Unidentified](),
            state = Source(sourceIdentifier, modifiedTime),
            deletedReason = DeletedFromSource("Mets")
          )
        )
      case false =>
        for {
          license <- parseLicense
          accessStatus <- parseAccessStatus
          item = Item[IdState.Unminted](
            id = IdState.Unidentifiable,
            locations = List(digitalLocation(license, accessStatus)))
        } yield
          Work.Invisible[Source](
            version = version,
            state = Source(sourceIdentifier, modifiedTime),
            data = WorkData[DataState.Unidentified](
              items = List(item),
              mergeCandidates = List(mergeCandidate),
              thumbnail =
                thumbnail(sourceIdentifier.value, license, accessStatus),
              imageData = imageData(version, license, accessStatus)
            ),
            invisibilityReasons = List(MetsWorksAreNotVisible)
          )
    }
  }

  private lazy val fileReferences: List[FileReference] =
    fileReferencesMapping.map { case (_, fileReference) => fileReference }

  private def mergeCandidate = MergeCandidate(
    identifier = SourceIdentifier(
      identifierType = IdentifierType("sierra-system-number"),
      ontologyType = "Work",
      // We lowercase the b number in the METS file so it matches the
      // case used by Sierra.
      // e.g. b20442233 has the identifier "B20442233" in the METS file,
      //
      value = recordIdentifier.toLowerCase
    ),
    reason = "METS work"
  )

  private def digitalLocation(license: Option[License],
                              accessStatus: Option[AccessStatus]) =
    DigitalLocation(
      url = s"https://wellcomelibrary.org/iiif/$recordIdentifier/manifest",
      locationType = LocationType.IIIFPresentationAPI,
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
      identifierType = IdentifierType("mets"),
      ontologyType = "Work",
      // We lowercase the b number in the METS file so it matches the
      // case used by Sierra.
      // e.g. b20442233 has the identifier "B20442233" in the METS file,
      //
      value = recordIdentifier.toLowerCase
    )

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
    accessStatus: Option[AccessStatus]): Option[DigitalLocation] =
    for {
      fileReference <- titlePageFileReference
        .orElse(fileReferences.find(ImageUtils.isThumbnail))
      url <- ImageUtils.buildThumbnailUrl(bnumber, fileReference)
      if !accessStatus.exists(_.hasRestrictions)
    } yield
      DigitalLocation(
        url = url,
        locationType = LocationType.ThumbnailImage,
        license = license
      )

  private def imageData(
    version: Int,
    license: Option[License],
    accessStatus: Option[AccessStatus]): List[ImageData[IdState.Identifiable]] =
    if (accessStatus.exists(_.hasRestrictions)) {
      Nil
    } else {
      fileReferences
        .filter(ImageUtils.isImage)
        .flatMap { fileReference =>
          ImageUtils.buildImageUrl(fileReference).map { url =>
            ImageData[IdState.Identifiable](
              id = IdState.Identifiable(
                sourceIdentifier = ImageUtils
                  .getImageSourceId(recordIdentifier, fileReference.id)
              ),
              version = version,
              locations = List(
                DigitalLocation(
                  url = url,
                  locationType = LocationType.IIIFImageAPI,
                  license = license,
                  accessConditions = accessConditions(accessStatus)
                ),
                digitalLocation(license, accessStatus)
              )
            )
          }
        }
    }

}
