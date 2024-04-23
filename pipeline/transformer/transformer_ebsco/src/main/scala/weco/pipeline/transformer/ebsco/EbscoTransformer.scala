package weco.pipeline.transformer.ebsco

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{DeletedReason, Work, WorkState}
import weco.catalogue.source_model.ebsco.{
  EbscoDeletedSourceData,
  EbscoSourceData,
  EbscoUpdatedSourceData
}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc.xml.transformers.MarcXMLRecordTransformer
import weco.pipeline.transformer.marc_common.logging.LoggingContext
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
      case EbscoUpdatedSourceData(s3Location) =>
        for {
          xmlString <- store.get(s3Location).left.map(_.e)
          xml <- Try(XML.loadString(xmlString.identifiedT)).toEither
        } yield createWork(MarcXMLRecord(xml))

      case EbscoDeletedSourceData =>
        Right(
          Work.Deleted[Source](
            version = version,
            // TODO: The adapter should provide the date & time
            state = Source(
              SourceIdentifier(IdentifierType.EbscoAltLookup, "Work", id),
              Instant.now()
            ),
            deletedReason =
              DeletedReason.DeletedFromSource("Deleted by EBSCO source")
          )
        )
    }

  private def createWork(record: MarcXMLRecord): Work.Visible[Source] = {
    val state = Source(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.EbscoAltLookup,
        ontologyType = "Work",
        value = record.controlField("001").get
      ),
      // TODO: The adapter should provide the date & time
      sourceModifiedTime = Instant.now
    )
    implicit val ctx: LoggingContext = LoggingContext(
      state.sourceIdentifier.value
    )
    Work.Visible[Source](
      version = 0,
      state = state,
      data = MarcXMLRecordTransformer(record)
    )
  }
}
