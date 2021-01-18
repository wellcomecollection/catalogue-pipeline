package weco.catalogue.source_model.config

import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.store.dynamo.{DynamoHashStore, DynamoHybridStore}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import weco.catalogue.source_model.store.SourceVHS

import scala.language.higherKinds

object SourceVHSBuilder {
  def build[T](config: Config, namespace: String = "vhs")(
    implicit codec: Codec[T]): SourceVHS[T] = {
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)
    implicit val dynamoClient: DynamoDbClient =
      DynamoBuilder.buildDynamoClient(config)

    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = namespace)

    implicit val indexedStore: DynamoHashStore[String, Int, S3ObjectLocation] =
      new DynamoHashStore[String, Int, S3ObjectLocation](dynamoConfig)

    implicit val typedStore: S3TypedStore[T] = S3TypedStore[T]

    class VHSInternalStore(prefix: S3ObjectLocationPrefix)(
      implicit
      indexedStore: DynamoHashStore[String, Int, S3ObjectLocation],
      typedStore: S3TypedStore[T]
    ) extends DynamoHybridStore[T](prefix)(indexedStore, typedStore)
        with HybridStoreWithMaxima[String, Int, S3ObjectLocation, T]

    val vhs =
      new VersionedHybridStore[String, Int, S3ObjectLocation, T](
        new VHSInternalStore(
          prefix = buildObjectLocationPrefix(config, namespace = namespace)
        )
      )

    new SourceVHS[T](vhs)
  }

  private def buildObjectLocationPrefix(config: Config, namespace: String) =
    S3ObjectLocationPrefix(
      bucket = config
        .requireString(s"aws.$namespace.s3.bucketName"),
      keyPrefix = config
        .getStringOption(s"aws.$namespace.s3.globalPrefix")
        .getOrElse("")
    )
}
