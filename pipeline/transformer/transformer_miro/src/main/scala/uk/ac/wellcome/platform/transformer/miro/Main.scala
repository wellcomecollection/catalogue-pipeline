package uk.ac.wellcome.platform.transformer.miro

import akka.actor.ActorSystem
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.platform.transformer.miro.Implicits._
import uk.ac.wellcome.platform.transformer.miro.services.{
  MiroDynamoVHSReader,
  MiroTransformerWorkerService
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    implicit val s3Client: AmazonS3 =
      S3Builder.buildS3Client(config)

    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    val miroVhsReader = new MiroDynamoVHSReader(
      config = DynamoBuilder.buildDynamoConfig(config)
    )

    new MiroTransformerWorkerService(
      stream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      sender = BigMessagingBuilder.buildBigMessageSender(config),
      miroVhsReader = miroVhsReader,
      typedStore = S3TypedStore[MiroRecord]
    )
  }
}
