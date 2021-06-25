package weco.pipeline.transformer.miro.services

import io.circe.Decoder
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{Transformer, TransformerWorker}
import weco.pipeline.transformer.miro.MiroRecordTransformer
import weco.pipeline.transformer.miro.models.MiroMetadata
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}
import weco.typesafe.Runnable

import scala.concurrent.ExecutionContext

class MiroTransformerWorker[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  miroReadable: Readable[S3ObjectLocation, MiroRecord],
  val retriever: Retriever[Work[Source]]
)(
  implicit
  val decoder: Decoder[MiroSourcePayload],
  val ec: ExecutionContext
) extends Runnable
    with TransformerWorker[
      MiroSourcePayload,
      (MiroRecord, MiroSourceOverrides, MiroMetadata),
      MsgDestination] {

  override val transformer
    : Transformer[(MiroRecord, MiroSourceOverrides, MiroMetadata)] =
    new MiroRecordTransformer

  override def lookupSourceData(p: MiroSourcePayload)
    : Either[ReadError,
             Identified[Version[String, Int],
                        (MiroRecord, MiroSourceOverrides, MiroMetadata)]] =
    miroReadable
      .get(p.location)
      .map {
        case Identified(_, miroRecord) =>
          Identified(
            Version(p.id, p.version),
            (
              miroRecord,
              p.overrides.getOrElse(MiroSourceOverrides.empty),
              MiroMetadata(
                isClearedForCatalogueAPI = p.isClearedForCatalogueAPI))
          )
      }
}
