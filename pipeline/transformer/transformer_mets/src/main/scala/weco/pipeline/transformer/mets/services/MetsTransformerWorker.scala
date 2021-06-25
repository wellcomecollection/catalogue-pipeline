package weco.pipeline.transformer.mets.services

import io.circe.Decoder
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.{Transformer, TransformerWorker}
import weco.pipeline.transformer.mets.transformer.MetsXmlTransformer
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}
import weco.typesafe.Runnable

import scala.concurrent.ExecutionContext

class MetsTransformerWorker[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  metsXmlStore: Readable[S3ObjectLocation, String],
  val retriever: Retriever[Work[Source]]
)(
  implicit
  val decoder: Decoder[MetsSourcePayload],
  val ec: ExecutionContext
) extends Runnable
    with TransformerWorker[MetsSourcePayload, MetsSourceData, MsgDestination] {

  override val transformer: Transformer[MetsSourceData] =
    new MetsXmlTransformer(metsXmlStore)

  override def lookupSourceData(p: MetsSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], MetsSourceData]] =
    Right(Identified(Version(p.id, p.version), p.sourceData))
}
