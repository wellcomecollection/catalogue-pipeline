package weco.pipeline.transformer.marc.xml.transformers

import weco.catalogue.internal_model.identifiers.{
  DataState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{Work, WorkData}
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc_common.transformers.MarcTitle

import java.time.Instant

object MarcXMLRecordTransformer {

  def apply(record: MarcXMLRecord): Work.Visible[Source] = {
    // TODO: The state stuff here is EBSCO-specific.  Move it when the EBSCO transformer is implemented
    val state = Source(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.EbscoAltLookup,
        ontologyType = "Work",
        value = record.controlField("001").get
      ),
      // TODO: I don't think we get sourceModifiedTime in the XML records from EBSCO,
      //   but we might be able to work something out
      sourceModifiedTime = Instant.now
    )
    Work.Visible[Source](
      version = 0,
      state = state,
      data = workDataFromMarcRecord(record)
    )
  }

  def workDataFromMarcRecord(
    record: MarcXMLRecord
  ): WorkData[DataState.Unidentified] = {
    WorkData[DataState.Unidentified](
      title = MarcTitle(record)
    )
  }
}
