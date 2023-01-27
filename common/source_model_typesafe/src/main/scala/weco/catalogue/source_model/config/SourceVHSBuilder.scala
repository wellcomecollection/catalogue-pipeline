package weco.catalogue.source_model.config

import com.typesafe.config.Config
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client
import weco.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import weco.storage.store.{HybridStoreWithMaxima, VersionedHybridStore}
import weco.storage.store.dynamo.{
  ConsistencyMode,
  DynamoHashStore,
  DynamoHybridStore,
  StronglyConsistent
}
import weco.storage.store.s3.S3TypedStore
import weco.storage.streaming.Codec
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.config.builders.EnrichConfig._
import weco.catalogue.source_model.store.SourceVHS

import scala.language.higherKinds

object SourceVHSBuilder {
  def build[T](config: Config, namespace: String = "vhs")(implicit
    codec: Codec[T]
  ): SourceVHS[T] = {
    implicit val s3Client: S3Client = S3Client.builder().build()

    implicit val dynamoClient: DynamoDbClient =
      DynamoDbClient.builder().build()

    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = namespace)

    // We need strong consistency here, because immediately after we write
    // a record, we want to be able to read the associated DynamoDB record
    // to create a SourcePayload to send to the transformers.
    implicit val consistencyMode: ConsistencyMode =
      StronglyConsistent

    implicit val indexedStore: DynamoHashStore[String, Int, S3ObjectLocation] =
      new DynamoHashStore[String, Int, S3ObjectLocation](dynamoConfig)

    implicit val typedStore: S3TypedStore[T] = S3TypedStore[T]

    class VHSInternalStore(prefix: S3ObjectLocationPrefix)(implicit
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
