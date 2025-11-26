package weco.pipeline.transformer.mets.generators

import weco.pipeline.transformer.mets.transformer.models.FileReference
import weco.pipeline.transformer.mets.transformer.InvisibleMetsData
import weco.pipeline.transformer.mets.transformers.ModsAccessConditions
import weco.sierra.generators.SierraIdentifierGenerators

import java.time.Instant

trait MetsDataGenerators extends SierraIdentifierGenerators {
  def createBibNumberString: String = createSierraBibNumber.withCheckDigit

  def createMetsDataWith(
    bibNumber: String = createBibNumberString,
    title: String = randomAlphanumeric(),
    accessConditionDz: Option[String] = None,
    accessConditionStatus: Option[String] = None,
    accessConditionUsage: Option[String] = None,
    fileReferences: List[FileReference] = Nil,
    thumbnailReference: Option[FileReference] = None,
    version: Int = 0,
    modifiedTime: Instant = Instant.ofEpochMilli(0),
    createdDate: Option[String] = None
  ): InvisibleMetsData =
    InvisibleMetsData(
      recordIdentifier = bibNumber,
      title = title,
      ModsAccessConditions(
        dz = accessConditionDz,
        status = accessConditionStatus,
        usage = accessConditionUsage
      ).parse.right.get,
      fileReferences = fileReferences,
      thumbnailReference = thumbnailReference,
      version = version,
      modifiedTime = modifiedTime,
      locationPrefix = "v2/",
      createdDate = createdDate
    )
}
