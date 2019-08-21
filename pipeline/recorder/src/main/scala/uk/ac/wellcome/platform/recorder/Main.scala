package uk.ac.wellcome.platform.recorder

import java.util.UUID
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

import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{HybridIndexedStoreEntry, HybridStoreWithMaxima, VersionedHybridStore}
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.dynamo.DynamoHashEntry
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.Version


class DynamoSingleVersionedHybridStore[Id, V, T, Metadata](
  store: DynamoSingleVersionHybridStoreWithMaxima[Id, V, T, Metadata])(
  implicit N: Numeric[V])
    extends VersionedHybridStore[Id, V, ObjectLocation, T, Metadata](store)

class DynamoSingleVersionHybridStoreWithMaxima[Id, V, T, Metadata](
  prefix: ObjectLocationPrefix)(
  implicit
  val indexedStore: DynamoHashStore[
    Id,
    V,
    HybridIndexedStoreEntry[ObjectLocation, Metadata]],
  val typedStore: S3TypedStore[T]
) extends HybridStoreWithMaxima[Id, V, ObjectLocation, T, Metadata] {

  override protected def createTypeStoreId(id: Version[Id, V]): ObjectLocation =
    prefix.asLocation(
      id.id.toString,
      id.version.toString,
      UUID.randomUUID().toString)
}

object Main extends WellcomeTypesafeApp {

  type IndexEntry = HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]

  type HashEntry = DynamoHashEntry[String, Int, IndexEntry]

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
      new DynamoHashStore[String, Int, IndexEntry](dynamoConfig)
    
    val dynamoHybridStore =
      new DynamoSingleVersionHybridStoreWithMaxima[String, Int, TransformedBaseWork, EmptyMetadata](
        objectLocationPrefix)

    new RecorderWorkerService(
      store = new DynamoSingleVersionedHybridStore(dynamoHybridStore),
      messageStream =
        BigMessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      msgSender = SNSBuilder.buildSNSMessageSender(
        config,
        namespace = "dunno",
        subject = "dunno")
    )
  }
}
