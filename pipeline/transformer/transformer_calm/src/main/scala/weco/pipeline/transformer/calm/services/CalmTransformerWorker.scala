package weco.pipeline.transformer.calm.services

import io.circe.Decoder
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.calm.CalmTransformer
import weco.pipeline.transformer.calm.models.CalmSourceData
import weco.pipeline.transformer.{Transformer, TransformerWorker}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}
import weco.typesafe.Runnable

import scala.concurrent.ExecutionContext

class CalmTransformerWorker[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  recordReadable: Readable[S3ObjectLocation, CalmRecord],
  val retriever: Retriever[Work[Source]]
)(
  implicit
  val decoder: Decoder[CalmSourcePayload],
  val ec: ExecutionContext
) extends Runnable
    with TransformerWorker[CalmSourcePayload, CalmSourceData, MsgDestination] {

  val transformer: Transformer[CalmSourceData] = CalmTransformer

  override def lookupSourceData(p: CalmSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], CalmSourceData]] =
    recordReadable
      .get(p.location)
      .map {
        case Identified(_, record) =>
          Identified(
            Version(p.id, p.version),
            CalmSourceData(
              record = record,
              isDeleted = p.isDeleted
            ))
      }
}
