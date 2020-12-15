package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.transformer.{Transformer, TransformerWorker}

class CalmTransformerWorker(
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            SNSConfig],
  store: VersionedStore[String, Int, CalmRecord]
) extends Runnable
    with TransformerWorker[CalmRecord, SNSConfig] {

  val transformer: Transformer[CalmRecord] = CalmTransformer

  override def lookupSourceData(id: String)
    : Either[ReadError, Identified[Version[String, Int], CalmRecord]] =
    store.getLatest(id)
}
