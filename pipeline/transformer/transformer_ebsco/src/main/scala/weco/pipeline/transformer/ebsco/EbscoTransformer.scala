package weco.pipeline.transformer.ebsco

import weco.catalogue.internal_model.identifiers.{
  DataState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.Format.EJournals
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{
  DeletedReason,
  Work,
  WorkData,
  WorkState
}
import weco.catalogue.source_model.ebsco.{
  EbscoDeletedSourceData,
  EbscoSourceData,
  EbscoUpdatedSourceData
}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.transformers._
import weco.pipeline.transformer.result.Result
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.Readable

import java.time.Instant
import scala.util.Try
import scala.xml.XML

class EbscoTransformer(store: Readable[S3ObjectLocation, String])
    extends Transformer[EbscoSourceData] {
  override def apply(
    id: String,
    sourceData: EbscoSourceData,
    version: Int
  ): Result[Work[WorkState.Source]] =
    sourceData match {
      case EbscoUpdatedSourceData(s3Location, modifiedTime) =>
        for {
          xmlString <- store.get(s3Location).left.map(_.e)
          xml <- Try(XML.loadString(xmlString.identifiedT)).toEither
        } yield createWork(MarcXMLRecord(xml), version, modifiedTime)

      case EbscoDeletedSourceData(modifiedTime) =>
        Right(
          Work.Deleted[Source](
            version = version,
            state = Source(
              SourceIdentifier(IdentifierType.EbscoAltLookup, "Work", id),
              modifiedTime
            ),
            deletedReason =
              DeletedReason.DeletedFromSource("Deleted by EBSCO source")
          )
        )
    }

  private def createWork(
    record: MarcXMLRecord,
    version: Int,
    modifiedTime: Instant
  ): Work.Visible[Source] = {
    val state = Source(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.EbscoAltLookup,
        ontologyType = "Work",
        value = record.controlField("001").get.content
      ),
      sourceModifiedTime = modifiedTime
    )
    implicit val ctx: LoggingContext = LoggingContext(
      state.sourceIdentifier.value
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
        contributors = MarcContributors(record).toList,
        subjects = MarcSubjects(record).toList,
        genres = MarcGenres(record).toList,
        holdings = MarcElectronicResources.toHoldings(record).toList,
        languages = MarcLanguage(record).toList,
        // TODO: We should rely on a Marc transformer to extract this information
        //   but we know that all the records we are transforming from this source
        //   are EJournals.
        format = Some(EJournals)
      )
    )
  }
}
