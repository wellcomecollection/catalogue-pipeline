package uk.ac.wellcome.bigmessaging.typesafe

import org.scanamo.auto._
import com.typesafe.config.Config
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB

import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix, Version}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.bigmessaging.{VHS, VHSInternalStore}

object VHSBuilder {

  type IndexStore =
    Store[Version[String, Int], ObjectLocation] with Maxima[String, Int]

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
    new VHS(
      new VHSInternalStore(
        objectLocationPrefix,
        createIndexStore(dynamoClient, dynamoConfig),
        S3TypedStore[T])
    )
  }

  private def buildObjectLocationPrefix(config: Config, namespace: String) =
    ObjectLocationPrefix(
      namespace = config
        .requireString(s"aws.${namespace}.s3.bucketName"),
      path = config
        .getStringOption(s"aws.${namespace}.s3.globalPrefix")
        .getOrElse("")
    )

  private def createIndexStore(dynamoClient: AmazonDynamoDB,
                               dynamoConfig: DynamoConfig): IndexStore = {
    implicit val dynamo = dynamoClient;
    new DynamoHashStore[String, Int, ObjectLocation](dynamoConfig)
  }
}
