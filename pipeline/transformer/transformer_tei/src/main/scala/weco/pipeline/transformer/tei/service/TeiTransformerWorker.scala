package weco.pipeline.transformer.tei.service

import io.circe.Decoder
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.TeiMetadata
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{Transformer, TransformerWorker}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.{Identified, ReadError, Version}
import weco.typesafe.Runnable

import scala.concurrent.ExecutionContext

class TeiTransformerWorker[MsgDestination](
  val transformer: Transformer[TeiMetadata],
  val retriever: Retriever[Work[WorkState.Source]],
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[WorkState.Source],
                                            MsgDestination])(
  implicit val ec: ExecutionContext,
  val decoder: Decoder[TeiSourcePayload])
    extends TransformerWorker[TeiSourcePayload, TeiMetadata, MsgDestination]
    with Runnable {

  override def lookupSourceData(payload: TeiSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], TeiMetadata]] =
    Right(Identified(Version(payload.id, payload.version), payload.metadata))
}
