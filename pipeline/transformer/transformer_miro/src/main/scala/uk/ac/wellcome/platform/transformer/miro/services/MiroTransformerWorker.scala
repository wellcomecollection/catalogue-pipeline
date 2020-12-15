package uk.ac.wellcome.platform.transformer.miro.services

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.miro.MiroRecordTransformer
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.transformer.{Transformer, TransformerWorker}

class MiroTransformerWorker[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  miroReadable: Readable[S3ObjectLocation, MiroRecord]
)(
  implicit val decoder: Decoder[MiroSourcePayload]
) extends Runnable
    with TransformerWorker[
      MiroSourcePayload,
      (MiroRecord, MiroMetadata),
      MsgDestination] {

  override val transformer: Transformer[(MiroRecord, MiroMetadata)] =
    new MiroRecordTransformer

  override def lookupSourceData(p: MiroSourcePayload)
    : Either[ReadError,
             Identified[Version[String, Int], (MiroRecord, MiroMetadata)]] =
    miroReadable
      .get(p.location)
      .map {
        case Identified(_, miroRecord) =>
          Identified(
            Version(p.id, p.version),
            (
              miroRecord,
              MiroMetadata(
                isClearedForCatalogueAPI = p.isClearedForCatalogueAPI)))
      }
}
