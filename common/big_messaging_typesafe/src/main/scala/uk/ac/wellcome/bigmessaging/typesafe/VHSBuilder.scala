package uk.ac.wellcome.bigmessaging.typesafe

import org.scanamo.{DynamoFormat, DynamoValue}
import org.scanamo.error.DynamoReadError
import org.scanamo.auto._
import com.typesafe.config.Config
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB

import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.dynamo.{DynamoConfig, DynamoHashEntry}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.HybridIndexedStoreEntry
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.bigmessaging.{
  EmptyMetadata,
  VHS,
  VHSInternalStore,
  VHSWithMetadata
}

object VHSBuilder {

  // Scanamo auto derivation cannot derive format for EmptyMetadata correctly.
  // An underscore is used for variable name as the compiler thinks it is unused
  // even though it isn't.
  private implicit val _: DynamoFormat[EmptyMetadata] =
    new DynamoFormat[EmptyMetadata] {
      override def read(
        av: DynamoValue): Either[DynamoReadError, EmptyMetadata] =
        Right(EmptyMetadata())
      override def write(t: EmptyMetadata): DynamoValue =
        DynamoValue.fromMap(Map.empty)
    }

  type WithMetaFormat[Metadata] =
    DynamoFormat[
      DynamoHashEntry[
        String,
        Int,
        HybridIndexedStoreEntry[ObjectLocation, Metadata]
      ]
    ]

  def build[T](config: Config, namespace: String = "vhs")(
    implicit codec: Codec[T]): VHS[T] =
    VHSBuilder.build(
      buildObjectLocationPrefix(config, namespace = namespace),
      DynamoBuilder.buildDynamoConfig(config, namespace = namespace),
      DynamoBuilder.buildDynamoClient(config),
      S3Builder.buildS3Client(config)
    )

  def build[T](objectLocationPrefix: ObjectLocationPrefix,
               dynamoConfig: DynamoConfig,
               dynamoClient: AmazonDynamoDB,
               s3Client: AmazonS3)(implicit codec: Codec[T]): VHS[T] = {
    implicit val s3 = s3Client;
    implicit val dynamo = dynamoClient;
    new VHS(
      new VHSInternalStore(
        objectLocationPrefix,
        new DynamoHashStore[
          String,
          Int,
          HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]
        ](dynamoConfig),
        S3TypedStore[T]
      )
    )
  }

  def buildWithMetadata[T, Metadata](config: Config, namespace: String = "vhs")(
    implicit
    codec: Codec[T],
    format: WithMetaFormat[Metadata]): VHSWithMetadata[T, Metadata] =
    VHSBuilder.buildWithMetadata(
      buildObjectLocationPrefix(config, namespace = namespace),
      DynamoBuilder.buildDynamoConfig(config, namespace = namespace),
      DynamoBuilder.buildDynamoClient(config),
      S3Builder.buildS3Client(config)
    )

  def buildWithMetadata[T, Metadata](objectLocationPrefix: ObjectLocationPrefix,
                                     dynamoConfig: DynamoConfig,
                                     dynamoClient: AmazonDynamoDB,
                                     s3Client: AmazonS3)(
    implicit
    codec: Codec[T],
    format: WithMetaFormat[Metadata]): VHSWithMetadata[T, Metadata] = {
    implicit val s3 = s3Client;
    implicit val dynamo = dynamoClient;
    new VHSWithMetadata(
      new VHSInternalStore(
        objectLocationPrefix,
        new DynamoHashStore[
          String,
          Int,
          HybridIndexedStoreEntry[ObjectLocation, Metadata]
        ](dynamoConfig),
        S3TypedStore[T]
      )
    )
  }

  private def buildObjectLocationPrefix(config: Config, namespace: String) =
    ObjectLocationPrefix(
      namespace = config.required(s"aws.${namespace}.s3.bucketName"),
      path = config.getOrElse(s"aws.${namespace}.s3.globalPrefix")(default = ""))
}
