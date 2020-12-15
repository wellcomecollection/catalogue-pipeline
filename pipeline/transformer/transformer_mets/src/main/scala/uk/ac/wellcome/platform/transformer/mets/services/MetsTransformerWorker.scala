package uk.ac.wellcome.platform.transformer.mets.services

import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream
import uk.ac.wellcome.platform.transformer.mets.transformer.MetsXmlTransformer
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.catalogue.transformer.{Transformer, TransformerWorker}

class MetsTransformerWorker[MsgDestination](
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            MsgDestination],
  metsXmlStore: Readable[S3ObjectLocation, String]
)(
  implicit val decoder: Decoder[MetsSourcePayload]
) extends Runnable
    with TransformerWorker[MetsSourcePayload, MetsSourceData, MsgDestination] {

  override val transformer: Transformer[MetsSourceData] =
    new MetsXmlTransformer(metsXmlStore)

  override def lookupSourceData(p: MetsSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], MetsSourceData]] =
    Right(Identified(Version(p.id, p.version), p.sourceData))
}
