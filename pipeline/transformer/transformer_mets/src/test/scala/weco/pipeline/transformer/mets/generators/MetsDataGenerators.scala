package weco.pipeline.transformer.mets.generators

import weco.pipeline.transformer.mets.transformer.{FileReference, InvisibleMetsData}
import weco.sierra.generators.SierraIdentifierGenerators

trait MetsDataGenerators extends SierraIdentifierGenerators {
  def createBibNumberString: String = createSierraBibNumber.withCheckDigit

  def createMetsDataWith(
    bibNumber: String = createBibNumberString,
    accessConditionDz: Option[String] = None,
    accessConditionStatus: Option[String] = None,
    accessConditionUsage: Option[String] = None,
    fileReferencesMapping: List[(String, FileReference)] = Nil,
    titlePageId: Option[String] = None): InvisibleMetsData =
    InvisibleMetsData(
      recordIdentifier = bibNumber,
      accessConditionDz = accessConditionDz,
      accessConditionStatus = accessConditionStatus,
      accessConditionUsage = accessConditionUsage,
      fileReferencesMapping = fileReferencesMapping,
      titlePageId = titlePageId
    )
}
