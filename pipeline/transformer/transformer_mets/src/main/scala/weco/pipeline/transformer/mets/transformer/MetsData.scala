package weco.pipeline.transformer.mets.transformer

import java.time.Instant
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.DeletedReason.DeletedFromSource
import weco.catalogue.internal_model.work.InvisibilityReason.MetsWorksAreNotVisible
import weco.catalogue.internal_model.work.{Item, Work, WorkData}
import weco.pipeline.transformer.mets.transformer.models.FileReference
import weco.pipeline.transformer.mets.transformers.{
  MetsAccessConditions,
  MetsImageData,
  MetsLocation,
  MetsMergeCandidate,
  MetsThumbnail,
  MetsTitle
}
import weco.pipeline.transformer.result.Result

sealed trait MetsData {
  val recordIdentifier: String

  def toWork: Work[Source]

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

case class DeletedMetsData(
  recordIdentifier: String,
  version: Int,
  modifiedTime: Instant
) extends MetsData {
  override def toWork: Work[Source] =
    Work.Deleted[Source](
      version = version,
      state = Source(sourceIdentifier, modifiedTime),
      deletedReason = DeletedFromSource("Mets")
    )
}

case class InvisibleMetsData(
  recordIdentifier: String,
  title: String,
  accessConditions: MetsAccessConditions,
  fileReferences: List[FileReference] = Nil,
  thumbnailReference: Option[FileReference] = None,
  version: Int,
  modifiedTime: Instant
) extends MetsData {

  def toWork: Work[Source] = {
    val location = MetsLocation(
      recordIdentifier = recordIdentifier,
      license = accessConditions.licence,
      accessStatus = accessConditions.accessStatus,
      accessConditionUsage = accessConditions.usage
    )
    val item = Item[IdState.Unminted](
      id = IdState.Unidentifiable,
      locations = List(location)
    )

    Work.Invisible[Source](
      version = version,
      state = Source(
        sourceIdentifier = sourceIdentifier,
        sourceModifiedTime = modifiedTime,
        mergeCandidates = List(MetsMergeCandidate(recordIdentifier))
      ),
      data = WorkData[DataState.Unidentified](
        title = Some(title),
        items = List(item),
        thumbnail = MetsThumbnail(
          thumbnailReference,
          sourceIdentifier.value,
          accessConditions.licence,
          accessConditions.accessStatus
        ),
        imageData = imageData(
          version,
          accessConditions.licence,
          accessConditions.accessStatus,
          location
        )
      ),
      invisibilityReasons = List(MetsWorksAreNotVisible)
    )
  }

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
        .map {
          fileReference =>
            MetsImageData(
              recordIdentifier,
              version,
              license,
              manifestLocation,
              fileReference
            )
        }
    }
}

object InvisibleMetsData {
  def apply(
    root: MetsXml,
    filesRoot: MetsXml,
    version: Int,
    modifiedTime: Instant
  ): Result[InvisibleMetsData] = {
    for {
      id <- root.recordIdentifier
      title <- MetsTitle(root.root)
      accessConditions <- filesRoot.accessConditions
    } yield InvisibleMetsData(
      recordIdentifier = id,
      title = title,
      accessConditions = accessConditions,
      fileReferences = filesRoot.fileReferences,
      thumbnailReference = filesRoot.thumbnailReference,
      version: Int,
      modifiedTime: Instant
    )
  }
}
