package uk.ac.wellcome.bigmessaging.typesafe

import org.scanamo.auto._
import com.typesafe.config.Config
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.bigmessaging.{VHS, VHSInternalStore}
import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}

object VHSBuilder {

  def build[T](config: Config, namespace: String = "vhs")(
    implicit codec: Codec[T]): VHS[T] = {
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)
    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = namespace)

    implicit val indexedStore: DynamoHashStore[String, Int, S3ObjectLocation] =
      new DynamoHashStore[String, Int, S3ObjectLocation](dynamoConfig)

    implicit val typedStore: S3TypedStore[T] = S3TypedStore[T]

    new VHS(
      new VHSInternalStore(
        prefix = buildObjectLocationPrefix(config, namespace = namespace)
      )
    )
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
