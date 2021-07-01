package weco.pipeline.transformer.tei

import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{DeletedReason, Work, WorkData, WorkState}
import weco.catalogue.source_model.tei.{TeiChangedMetadata, TeiDeletedMetadata, TeiMetadata}
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.result.Result
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Store

class TeiTransformer(store: Store[S3ObjectLocation, String]) extends Transformer[TeiMetadata]{
  override def apply(id: String, sourceData: TeiMetadata, version: Int): Result[Work[WorkState.Source]] = sourceData match {
    case TeiChangedMetadata(s3Location, time) =>
      for {
        xmlString <- store.get(s3Location).left.map(_.e)
        teiXml<-TeiXml(id, xmlString.identifiedT)
        teiData <- TeiDataParser.parse(teiXml)
      } yield teiData.toWork(time, version)
    case TeiDeletedMetadata(time) => Right(Work.Deleted[Source](version, WorkData(), Source(SourceIdentifier(IdentifierType.Tei, "Work", id), time), DeletedReason.DeletedFromSource("Deleted by TEI source")))
  }
}
