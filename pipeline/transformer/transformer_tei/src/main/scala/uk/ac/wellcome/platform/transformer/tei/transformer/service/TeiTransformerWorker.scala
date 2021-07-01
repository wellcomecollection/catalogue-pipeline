package uk.ac.wellcome.platform.transformer.tei.transformer.service

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.transformer.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.tei.TeiMetadata

import scala.concurrent.ExecutionContext

class TeiTransformerWorker[MsgDestination](val transformer: Transformer[TeiMetadata],  val retriever: Retriever[Work[WorkState.Source]], val pipelineStream: PipelineStorageStream[NotificationMessage, Work[WorkState.Source], MsgDestination])(implicit val ec: ExecutionContext, val decoder: Decoder[TeiSourcePayload]) extends Runnable
  with TransformerWorker[TeiSourcePayload, TeiMetadata, MsgDestination]{

  override def lookupSourceData(payload: TeiSourcePayload): Either[ReadError, Identified[Version[String, Int], TeiMetadata]] = Right(Identified(Version(payload.id, payload.version), payload.metadata))
}
