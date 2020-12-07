package uk.ac.wellcome.platform.transformer.mets.service

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.mets_adapter.models.MetsSourceData
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.mets.transformer.MetsXmlTransformer
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.{Readable, VersionedStore}
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class MetsTransformerWorkerService[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  adapterStore: VersionedStore[String, Int, MetsSourceData],
  metsXmlStore: Readable[S3ObjectLocation, String]
) extends Runnable
    with TransformerWorker[MetsSourceData, MsgDestination] {

  override val transformer: Transformer[MetsSourceData] =
    new MetsXmlTransformer(metsXmlStore)

  override def lookupSourceData(id: String): Either[ReadError, Identified[Version[String, Int], MetsSourceData]] =
    adapterStore.getLatest(id)
}
