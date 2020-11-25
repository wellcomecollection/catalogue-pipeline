package uk.ac.wellcome.platform.transformer.mets

import akka.actor.ActorSystem
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.platform.transformer.mets.service.MetsTransformerWorkerService
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext
import org.scanamo.auto._
import org.scanamo.time.JavaTimeFormats._
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.config.builders.AWSClientConfigBuilder

object Main extends WellcomeTypesafeApp with AWSClientConfigBuilder {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    new MetsTransformerWorkerService(
      SQSBuilder.buildSQSStream[NotificationMessage](config),
      sender = BigMessagingBuilder.buildBigMessageSender(config),
      adapterStore = new DynamoSingleVersionStore[String, MetsLocation](
        DynamoBuilder.buildDynamoConfig(config, namespace = "mets")
      ),
      metsXmlStore = S3TypedStore[String]
    )
  }
}
