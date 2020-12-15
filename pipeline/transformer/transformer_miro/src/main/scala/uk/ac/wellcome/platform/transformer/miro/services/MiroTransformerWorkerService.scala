package uk.ac.wellcome.platform.transformer.miro.services

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
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.transformer.{Transformer, TransformerWorker}

class MiroTransformerWorkerService[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  miroVhsReader: Readable[String, MiroVHSRecord],
  typedStore: Readable[S3ObjectLocation, MiroRecord]
) extends Runnable
    with TransformerWorker[(MiroRecord, MiroMetadata), MsgDestination] {

  override val transformer: Transformer[(MiroRecord, MiroMetadata)] =
    new MiroRecordTransformer

  private val miroLookup = new MiroLookup(miroVhsReader, typedStore)

  override def lookupSourceData(id: String)
    : Either[ReadError,
             Identified[Version[String, Int], (MiroRecord, MiroMetadata)]] =
    miroLookup.lookupRecord(id)
}
