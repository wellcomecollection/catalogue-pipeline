package uk.ac.wellcome.platform.transformer.miro

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import org.scanamo.DynamoFormat

import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.services.{
  MiroTransformerWorkerService,
  MiroVHSRecordReceiver
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata

import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.messaging.sns.SNSConfig

import uk.ac.wellcome.storage.store.dynamo.{DynamoHashStore, DynamoHybridStore}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.HybridIndexedStoreEntry
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.dynamo.DynamoHashEntry
import uk.ac.wellcome.storage.streaming.Codec._

object Main extends WellcomeTypesafeApp {

  type IndexEntry = HybridIndexedStoreEntry[ObjectLocation, MiroMetadata]

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
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val dynamoClient =
      DynamoBuilder.buildDynamoClient(config)
    implicit val transformedStore =
      S3TypedStore[TransformedBaseWork]
    implicit val mirmoRecordStore =
      S3TypedStore[MiroRecord]
    implicit val dynamoIndexStore =
      new DynamoHashStore[String, Int, IndexEntry](dynamoConfig)

    val vhsRecordReceiver = new MiroVHSRecordReceiver[SNSConfig](
      msgSender =
        BigMessagingBuilder.buildBigMessageSender[TransformedBaseWork](config),
      store =
        new DynamoHybridStore[MiroRecord, MiroMetadata](objectLocationPrefix)
    )

    new MiroTransformerWorkerService(
      vhsRecordReceiver = vhsRecordReceiver,
      miroTransformer = new MiroRecordTransformer,
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    )
  }
}
