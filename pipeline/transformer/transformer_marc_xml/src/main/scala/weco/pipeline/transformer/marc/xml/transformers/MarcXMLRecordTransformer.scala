package weco.pipeline.transformer.marc.xml.transformers

import weco.catalogue.internal_model.identifiers.{
  DataState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{Relations, Work, WorkData}
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.transformers.{
  MarcAlternativeTitles,
  MarcCollectionPath,
  MarcContributors,
  MarcCurrentFrequency,
  MarcDescription,
  MarcDesignation,
  MarcEdition,
  MarcElectronicResources,
  MarcFormat,
  MarcGenres,
  MarcInternationalStandardIdentifiers,
  MarcLanguage,
  MarcParents,
  MarcProduction,
  MarcSubjects,
  MarcTitle
}

import java.time.Instant

object MarcXMLRecordTransformer {
  def apply(
    record: MarcXMLRecord,
    version: Int,
    modifiedTime: Instant
  )(
    implicit ctx: LoggingContext
  ): Work.Visible[Source] = {
    val state = Source(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.EbscoAltLookup,
        ontologyType = "Work",
        value = record.controlField("001").get.content
      ),
      relations = Relations(ancestors = MarcParents(record)),
      sourceModifiedTime = modifiedTime
    )

    Work.Visible[Source](
      version = version,
      state = state,
      data = WorkData[DataState.Unidentified](
        title = MarcTitle(record),
        alternativeTitles = MarcAlternativeTitles(record).toList,
        otherIdentifiers = MarcInternationalStandardIdentifiers(record).toList,
        designation = MarcDesignation(record).toList,
        description = MarcDescription(record),
        currentFrequency = MarcCurrentFrequency(record),
        edition = MarcEdition(record),
        items = MarcElectronicResources(record).toList,
        contributors = MarcContributors(record).toList,
        subjects = MarcSubjects(record).toList,
        genres = MarcGenres(record).toList,
        languages = MarcLanguage(record).toList,
        production = MarcProduction(record),
        collectionPath = MarcCollectionPath(record),
        format = MarcFormat(record)
      )
    )
  }
}
