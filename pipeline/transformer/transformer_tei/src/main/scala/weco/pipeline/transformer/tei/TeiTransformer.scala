package weco.pipeline.transformer.tei

import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{DeletedReason, Work, WorkState}
import weco.catalogue.source_model.tei.{
  TeiChangedMetadata,
  TeiDeletedMetadata,
  TeiMetadata
}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.transformers.TeiLanguages
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Store

import java.time.Instant

class TeiTransformer(store: Store[S3ObjectLocation, String])
    extends Transformer[TeiMetadata] {
  override def apply(id: String,
                     sourceData: TeiMetadata,
                     version: Int): Result[Work[WorkState.Source]] =
    sourceData match {
      case TeiChangedMetadata(s3Location, time) =>
        handleTeiChange(id, version, s3Location, time)
      case TeiDeletedMetadata(time) =>
        handleTeiDelete(id, version, time)
    }

  private def handleTeiDelete(id: String, version: Int, time: Instant) = {
    Right(
      Work.Deleted[Source](
        version = version,
        state = Source(SourceIdentifier(IdentifierType.Tei, "Work", id), time),
        deletedReason = DeletedReason.DeletedFromSource("Deleted by TEI source")
      ))
  }

  private def handleTeiChange(id: String,
                              version: Int,
                              s3Location: S3ObjectLocation,
                              time: Instant) = {
    for {
      xmlString <- store.get(s3Location).left.map(_.e)
      teiXml <- TeiXml(id, xmlString.identifiedT)
      teiData <- parse(teiXml)
    } yield teiData.toWork(time, version)
  }

  private def parse(teiXml: TeiXml): Either[Throwable, TeiData] =
    for {
      summary <- teiXml.summary
      bNumber <- teiXml.bNumber
      title <- teiXml.title
      languages <- TeiLanguages(teiXml.xml)
      nestedData <- teiXml.nestedTeiData
    } yield TeiData(teiXml.id, title, bNumber, summary, languages, nestedData)
}
