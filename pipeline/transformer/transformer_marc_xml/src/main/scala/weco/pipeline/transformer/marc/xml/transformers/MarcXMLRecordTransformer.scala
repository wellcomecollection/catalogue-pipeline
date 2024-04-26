package weco.pipeline.transformer.marc.xml.transformers

import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.work.WorkData
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.transformers.{
  MarcAlternativeTitles,
  MarcContributors,
  MarcCurrentFrequency,
  MarcDescription,
  MarcDesignation,
  MarcEdition,
  MarcElectronicResources,
  MarcInternationalStandardIdentifiers,
  MarcSubjects,
  MarcTitle
}

object MarcXMLRecordTransformer {
  def apply(
    record: MarcXMLRecord
  )(
    implicit ctx: LoggingContext
  ): WorkData[DataState.Unidentified] = {
    WorkData[DataState.Unidentified](
      title = MarcTitle(record),
      alternativeTitles = MarcAlternativeTitles(record).toList,
      otherIdentifiers = MarcInternationalStandardIdentifiers(record).toList,
      designation = MarcDesignation(record).toList,
      description = MarcDescription(record),
      currentFrequency = MarcCurrentFrequency(record),
      edition = MarcEdition(record),
      items = MarcElectronicResources(record).toList,
      contributors = MarcContributors(record).toList,
      subjects = MarcSubjects(record).toList
    )
  }
}
