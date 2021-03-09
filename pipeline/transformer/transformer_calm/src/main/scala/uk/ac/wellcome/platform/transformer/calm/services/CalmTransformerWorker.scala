package uk.ac.wellcome.platform.transformer.calm.services

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.transformer.calm.CalmTransformer
import uk.ac.wellcome.platform.transformer.calm.models.CalmSourceData
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.transformer.{Transformer, TransformerWorker}

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
