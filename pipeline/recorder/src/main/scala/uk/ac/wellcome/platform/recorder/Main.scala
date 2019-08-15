package uk.ac.wellcome.platform.recorder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.typesafe.SNSBuilder
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}
import uk.ac.wellcome.storage.store.HybridIndexedStoreEntry
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.storage.store.dynamo.{DynamoHashStore, DynamoHybridStore}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}

case class EmptyMetadata()

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    implicit val typedStore: S3TypedStore[TransformedBaseWork] =
      S3TypedStore[TransformedBaseWork]

    type DynamoStoreEntry =
      HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]

    implicit val indexedStore: DynamoHashStore[String, Int, DynamoStoreEntry] =
      new DynamoHashStore[String, Int, TransformedBaseWork](
        DynamoBuilder.buildDynamoConfig(config))

    val vhs =
      new DynamoHybridStore[TransformedBaseWork, EmptyMetadata](
        ObjectLocationPrefix("what", "the/hell"))

    new RecorderWorkerService(
      vhs = vhs,
      messageStream =
        BigMessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the recorder")
    )
  }
}
