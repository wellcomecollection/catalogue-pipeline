package uk.ac.wellcome.platform.transformer.miro.services

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.miro.MiroRecordTransformer
import uk.ac.wellcome.platform.transformer.miro.models.{
  MiroMetadata,
  MiroVHSRecord
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.ReadError
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class MiroTransformerWorkerService[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  val sender: MessageSender[MsgDestination],
  miroVhsReader: Readable[String, MiroVHSRecord],
  typedStore: Readable[S3ObjectLocation, MiroRecord]
) extends Runnable
    with TransformerWorker[(MiroRecord, MiroMetadata, Int), MsgDestination] {

  override val transformer: Transformer[(MiroRecord, MiroMetadata, Int)] =
    new MiroRecordTransformer

  private val miroLookup = new MiroLookup(miroVhsReader, typedStore)

  override protected def lookupSourceData(
    key: StoreKey): Either[ReadError, (MiroRecord, MiroMetadata, Int)] =
    miroLookup.lookupRecord(key.id)
}
