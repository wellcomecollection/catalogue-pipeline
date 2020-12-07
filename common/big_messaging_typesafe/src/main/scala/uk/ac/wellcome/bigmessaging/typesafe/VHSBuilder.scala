package uk.ac.wellcome.bigmessaging.typesafe

import org.scanamo.auto._
import com.typesafe.config.Config
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.bigmessaging.{VHS, VHSInternalStore}
import uk.ac.wellcome.storage.s3.{S3ObjectLocation, S3ObjectLocationPrefix}

object VHSBuilder {

  type IndexStore =
    Store[Version[String, Int], S3ObjectLocation] with Maxima[String, Int]

  def build[T](config: Config, namespace: String = "vhs")(
    implicit codec: Codec[T]): VHS[T] = {
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)
    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = namespace)

    new VHS(
      new VHSInternalStore(
        prefix = buildObjectLocationPrefix(config, namespace = namespace),
        indexedStore =
          new DynamoHashStore[String, Int, S3ObjectLocation](dynamoConfig),
        typedStore = S3TypedStore[T])
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
