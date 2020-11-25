package uk.ac.wellcome.platform.transformer.mets.service

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.platform.transformer.mets.transformer.MetsXmlTransformer
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.{Readable, VersionedStore}
import uk.ac.wellcome.storage.{Identified, ReadError}
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class MetsTransformerWorkerService[MsgDestination](
  val stream: SQSStream[NotificationMessage],
  val sender: MessageSender[MsgDestination],
  adapterStore: VersionedStore[String, Int, MetsLocation],
  metsXmlStore: Readable[S3ObjectLocation, String]
) extends Runnable
    with TransformerWorker[MetsLocation, MsgDestination] {

  override val transformer: Transformer[MetsLocation] =
    new MetsXmlTransformer(metsXmlStore)

  override protected def lookupRecord(key: StoreKey): Either[ReadError, Identified[StoreKey, MetsLocation]] =
    adapterStore.get(key)
}
