package uk.ac.wellcome.platform.transformer.mets.service

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.mets.transformer.MetsXmlTransformer
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.{Readable, VersionedStore}
import uk.ac.wellcome.storage.{Identified, ReadError}
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class MetsTransformerWorkerService[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  val sender: MessageSender[MsgDestination],
  adapterStore: VersionedStore[String, Int, MetsLocation],
  metsXmlStore: Readable[S3ObjectLocation, String]
) extends Runnable
    with TransformerWorker[MetsLocation, MsgDestination] {

  override val transformer: Transformer[MetsLocation] =
    new MetsXmlTransformer(metsXmlStore)

  override protected def lookupSourceData(
    key: StoreKey): Either[ReadError, MetsLocation] =
    adapterStore
      .getLatest(key.id)
      .map { case Identified(_, metsLocation) => metsLocation }
}
