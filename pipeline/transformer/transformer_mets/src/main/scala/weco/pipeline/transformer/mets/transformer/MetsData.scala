package weco.pipeline.transformer.mets.transformer

import java.time.Instant
import cats.syntax.traverse._
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.DeletedReason.DeletedFromSource
import weco.catalogue.internal_model.work.InvisibilityReason.MetsWorksAreNotVisible
import weco.catalogue.internal_model.work.{Item, MergeCandidate, Work, WorkData}
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._
import weco.pipeline.transformer.mets.transformer.models.FileReference
import weco.pipeline.transformer.mets.transformers.{
  MetsAccessStatus,
  MetsLocation,
  MetsThumbnail
}
import weco.pipeline.transformer.result.Result

sealed trait MetsData {
  val recordIdentifier: String

  def toWork(version: Int, modifiedTime: Instant): Result[Work[Source]]

  protected def sourceIdentifier: SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.METS,
      ontologyType = "Work",
      // We lowercase the b number in the METS file so it matches the
      // case used by Sierra.
      // e.g. b20442233 has the identifier "B20442233" in the METS file,
      //
      value = recordIdentifier.toLowerCase
    )
}

case class DeletedMetsData(recordIdentifier: String) extends MetsData {
  override def toWork(
    version: Int,
    modifiedTime: Instant
  ): Either[Throwable, Work[Source]] =
    Right(
      Work.Deleted[Source](
        version = version,
        state = Source(sourceIdentifier, modifiedTime),
        deletedReason = DeletedFromSource("Mets")
      )
    )
}

case class InvisibleMetsData(
  recordIdentifier: String,
  title: String,
  accessConditionDz: Option[String] = None,
  accessConditionStatus: Option[String] = None,
  accessConditionUsage: Option[String] = None,
  fileReferencesMapping: List[(String, FileReference)] = Nil,
  thumbnailReference: Option[FileReference] = None,
  titlePageId: Option[String] = None
) extends MetsData {

  def toWork(version: Int, modifiedTime: Instant): Result[Work[Source]] =
    for {
      license <- parseLicense
      accessStatus <- MetsAccessStatus(accessConditionStatus)
      location = MetsLocation(
        recordIdentifier = recordIdentifier,
        license = license,
        accessStatus = accessStatus,
        accessConditionUsage = accessConditionUsage
      )
      item = Item[IdState.Unminted](
        id = IdState.Unidentifiable,
        locations = List(location)
      )

      work = Work.Invisible[Source](
        version = version,
        state = Source(
          sourceIdentifier = sourceIdentifier,
          sourceModifiedTime = modifiedTime,
          mergeCandidates = List(mergeCandidate)
        ),
        data = WorkData[DataState.Unidentified](
          title = Some(title),
          items = List(item),
          thumbnail = MetsThumbnail(
            thumbnailReference,
            sourceIdentifier.value,
            license,
            accessStatus
          ),
          imageData = imageData(version, license, accessStatus, location)
        ),
        invisibilityReasons = List(MetsWorksAreNotVisible)
      )
    } yield work

  private lazy val fileReferences: List[FileReference] =
    fileReferencesMapping.map { case (_, fileReference) => fileReference }

  private def mergeCandidate = MergeCandidate(
    identifier = SourceIdentifier(
      identifierType = IdentifierType.SierraSystemNumber,
      ontologyType = "Work",
      // We lowercase the b number in the METS file so it matches the
      // case used by Sierra.
      // e.g. b20442233 has the identifier "B20442233" in the METS file,
      //
      value = recordIdentifier.toLowerCase
    ).validatedWithWarning.getOrElse(
      throw new RuntimeException(
        s"METS works must have a valid Sierra merge candidate: ${recordIdentifier.toLowerCase} is not valid."
      )
    ),
    reason = "METS work"
  )

  private def parseLicense: Result[Option[License]] =
    accessConditionDz.map {
      // A lot of METS record have "Copyright not cleared"
      // or "rightsstatements.org/page/InC/1.0/?language=en" as dz access condition.
      // They both need to be mapped to a InCopyright license so hardcoding here
      //
      // Discussion about whether it's okay to map "all rights reserved" to
      // "in copyright": https://wellcome.slack.com/archives/CBT40CMKQ/p1621243064241400
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
        License.values.find {
          license =>
            equalsIgnoreCase(license.id, accessCondition) || equalsIgnoreCase(
              license.label,
              accessCondition
            ) || license.url.equals(accessCondition)

        } match {
          case Some(license) => Right(license)
          case None =>
            Left(new Exception(s"Couldn't match $accessCondition to a license"))
        }
    }.sequence

  private def imageData(
    version: Int,
    license: Option[License],
    accessStatus: Option[AccessStatus],
    manifestLocation: DigitalLocation
  ): List[ImageData[IdState.Identifiable]] =
    if (accessStatus.exists(_.hasRestrictions)) {
      Nil
    } else {
      fileReferences
        .filter(ImageUtils.isImage)
        .flatMap {
          fileReference =>
            ImageUtils.buildImageUrl(fileReference).map {
              url =>
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
                      accessConditions = manifestLocation.accessConditions
                    ),
                    manifestLocation
                  )
                )
            }
        }
    }
}
