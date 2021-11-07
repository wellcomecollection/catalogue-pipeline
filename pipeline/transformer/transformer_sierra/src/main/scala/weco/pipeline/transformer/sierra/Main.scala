package weco.pipeline.transformer.sierra

import com.amazonaws.services.s3.AmazonS3
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.json.JsonUtil._
import weco.pipeline.transformer.sierra.services.SierraSourceDataRetriever
import weco.pipeline.transformer.{Transformer, TransformerMain}
import weco.storage.store.s3.S3TypedStore

object Main extends TransformerMain[SierraSourcePayload, SierraTransformable] {
  override val sourceName: String = "Sierra"

  override def createTransformer(implicit s3Client: AmazonS3): Transformer[SierraTransformable] =
    (id: String, transformable: SierraTransformable, version: Int) =>
      SierraTransformer(transformable, version).toEither

  override def createSourceDataRetriever(implicit s3Client: AmazonS3) =
    new SierraSourceDataRetriever(
      sierraReadable = S3TypedStore[SierraTransformable]
    )

  override implicit val decoder: Decoder[SierraSourcePayload] =
    deriveConfiguredDecoder
}
