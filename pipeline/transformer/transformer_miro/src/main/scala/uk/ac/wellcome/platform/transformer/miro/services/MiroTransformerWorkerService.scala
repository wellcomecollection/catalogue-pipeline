package uk.ac.wellcome.platform.transformer.miro.services

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.transformer.miro.MiroRecordTransformer
import uk.ac.wellcome.platform.transformer.miro.models.{MiroMetadata, MiroVHSRecord}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.{Identified, ReadError}
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class MiroTransformerWorkerService[MsgDestination](
  val stream: SQSStream[NotificationMessage],
  val sender: MessageSender[MsgDestination],
  miroIndexStore: Readable[String, MiroVHSRecord],
  typedStore: Readable[S3ObjectLocation, MiroRecord]
) extends Runnable
    with TransformerWorker[(MiroRecord, MiroMetadata, Int), MsgDestination] {

  override val transformer: Transformer[(MiroRecord, MiroMetadata, Int)] =
    new MiroRecordTransformer

  private val miroLookup = new MiroLookup(miroIndexStore, typedStore)

  override protected def lookupSourceData(key: StoreKey): Either[ReadError, Identified[StoreKey, (MiroRecord, MiroMetadata, Int)]] =
    miroLookup
      .lookupRecord(key.id)
      .map { Identified(key, _) }
}
