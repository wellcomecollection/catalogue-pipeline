package uk.ac.wellcome.platform.recorder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.scanamo.DynamoFormat

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.{RecorderWorkerService, EmptyMetadata}
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.messaging.typesafe.SNSBuilder
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder

import uk.ac.wellcome.storage.store.dynamo.{
  DynamoVersionedHybridStore,
  DynamoHashRangeStore,
  DynamoHybridStoreWithMaxima
}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.HybridIndexedStoreEntry
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.dynamo.DynamoHashRangeEntry
import uk.ac.wellcome.storage.streaming.Codec._

object Main extends WellcomeTypesafeApp {

  type IndexEntry = HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]

  type HashEntry = DynamoHashRangeEntry[String, Int, IndexEntry]

  runWithConfig { config: Config =>
    // TODO: from where do we get the correct values for this?
    val objectLocationPrefix = ObjectLocationPrefix("namespace", "path")
    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = "namespace")

    // For some reason these dont get derived with scanamo auto derivation
    implicit def indexEntryFormat: DynamoFormat[IndexEntry] =
      DynamoFormat[IndexEntry]
    implicit def hashEntryFormat: DynamoFormat[HashEntry] =
      DynamoFormat[HashEntry]

    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val dynamoClient =
      DynamoBuilder.buildDynamoClient(config)
    implicit val s3Store =
      S3TypedStore[TransformedBaseWork]
    implicit val dynamoIndexStore =
      new DynamoHashRangeStore[String, Int, IndexEntry](dynamoConfig)
    
    val dynamoHybridStore =
      new DynamoHybridStoreWithMaxima[String, Int, TransformedBaseWork, EmptyMetadata](
        objectLocationPrefix)

    new RecorderWorkerService(
      // TODO: bad implementation as will keep old versions lying around (with
      // hash range stores the product of both keys defines unqiquness)
      store = new DynamoVersionedHybridStore(dynamoHybridStore),
      messageStream =
        BigMessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      msgSender = SNSBuilder.buildSNSMessageSender(
        config,
        namespace = "dunno",
        subject = "dunno")
    )
  }
}
