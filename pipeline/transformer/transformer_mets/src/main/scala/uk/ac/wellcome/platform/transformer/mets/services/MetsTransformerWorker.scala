package uk.ac.wellcome.platform.transformer.mets.services

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.work.WorkState.Source
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.transformer.mets.transformer.MetsXmlTransformer
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.internal_model.work.Work
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.catalogue.transformer.{Transformer, TransformerWorker}

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
