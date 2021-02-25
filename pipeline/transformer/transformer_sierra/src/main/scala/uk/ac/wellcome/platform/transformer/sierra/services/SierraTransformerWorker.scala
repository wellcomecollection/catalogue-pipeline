package uk.ac.wellcome.platform.transformer.sierra.services

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.{result, Work}
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformer
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.sierra_adapter.models.SierraTransformable
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.transformer.{Transformer, TransformerWorker}

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
    new Transformer[SierraTransformable] {
      override def apply(transformable: SierraTransformable,
                         version: Int): result.Result[Work[Source]] =
        SierraTransformer(transformable, version).toEither
    }

  override def lookupSourceData(p: SierraSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], SierraTransformable]] =
    sierraReadable
      .get(p.location)
      .map {
        case Identified(_, record) =>
          Identified(Version(p.id, p.version), record)
      }
}
