package weco.pipeline.transformer.tei

import com.amazonaws.services.s3.AmazonS3
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.TeiMetadata
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.tei.service.TeiSourceDataRetriever
import weco.storage.store.s3.S3TypedStore

object Main extends TransformerMain[TeiSourcePayload, TeiMetadata] {
  override val sourceName: String = "TEI"

  // TODO: This should take an instance of Readable
  override def createTransformer(implicit s3Client: AmazonS3) =
    new TeiTransformer(store = S3TypedStore[String])

  override def createSourceDataRetriever(implicit s3Client: AmazonS3) =
    new TeiSourceDataRetriever

  override implicit val decoder: Decoder[TeiSourcePayload] =
    deriveConfiguredDecoder
}
