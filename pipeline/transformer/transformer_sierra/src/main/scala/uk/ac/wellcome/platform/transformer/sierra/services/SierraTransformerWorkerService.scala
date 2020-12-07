package uk.ac.wellcome.platform.transformer.sierra.services

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformer
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.{Identified, ReadError}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class SierraTransformerWorkerService[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  store: VersionedStore[String, Int, SierraTransformable],
) extends Runnable
    with TransformerWorker[SierraTransformable, MsgDestination] {

  override val transformer: Transformer[SierraTransformable] =
    (input: SierraTransformable, version: Int) =>
      SierraTransformer(input, version).toEither

  override protected def lookupSourceData(
    key: StoreKey): Either[ReadError, SierraTransformable] =
    store
      .getLatest(key.id)
      .map { case Identified(_, sierraTransformable) => sierraTransformable }
}
