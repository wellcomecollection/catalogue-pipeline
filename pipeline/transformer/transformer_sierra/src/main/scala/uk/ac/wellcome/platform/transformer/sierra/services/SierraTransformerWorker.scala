package uk.ac.wellcome.platform.transformer.sierra.services

import io.circe.Decoder
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformer
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}
import weco.typesafe.Runnable
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.transformer.{Transformer, TransformerWorker}
import weco.pipeline_storage.{PipelineStorageStream, Retriever}

import scala.concurrent.ExecutionContext

class SierraTransformerWorker[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  sierraReadable: Readable[S3ObjectLocation, SierraTransformable],
  val retriever: Retriever[Work[Source]]
)(
  implicit
  val decoder: Decoder[SierraSourcePayload],
  val ec: ExecutionContext
) extends Runnable
    with TransformerWorker[
      SierraSourcePayload,
      SierraTransformable,
      MsgDestination] {

  override val transformer: Transformer[SierraTransformable] =
    (id: String, transformable: SierraTransformable, version: Int) =>
      SierraTransformer(transformable, version).toEither

  override def lookupSourceData(p: SierraSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], SierraTransformable]] =
    sierraReadable
      .get(p.location)
      .map {
        case Identified(_, record) =>
          Identified(Version(p.id, p.version), record)
      }
}
