package uk.ac.wellcome.platform.transformer.mets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.mets_adapter.models.MetsData
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.mets.client.{AmazonS3ClientFactory, AssumeRoleClientProvider}
import uk.ac.wellcome.platform.transformer.mets.service.MetsTransformerWorkerService
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import org.scanamo.auto._
import uk.ac.wellcome.platform.transformer.mets.store.TemporaryCredentialsStore
import uk.ac.wellcome.typesafe.config.builders.AWSClientConfigBuilder
import scala.concurrent.duration._

object Main extends WellcomeTypesafeApp with AWSClientConfigBuilder{
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val msgStore =
      S3TypedStore[TransformedBaseWork]
    implicit val dynamoClilent: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    val stsClient = AWSSecurityTokenServiceClientBuilder.standard().withRegion("eu-west-1").build()
    val s3ClientFactory = new AmazonS3ClientFactory(buildAWSClientConfig(config, namespace = "storage"))
    val assumeRoleS3ClientProvider = new AssumeRoleClientProvider[AmazonS3](stsClient, config.required[String]("aws.s3.storage.role.arn"), 20 minutes)(s3ClientFactory)
    val temporaryCredentialsStore = new TemporaryCredentialsStore[String](assumeRoleS3ClientProvider)

    new MetsTransformerWorkerService(
      SQSBuilder.buildSQSStream[NotificationMessage](config),
      messageSender = BigMessagingBuilder
        .buildBigMessageSender[TransformedBaseWork](config),
      adapterStore = new DynamoSingleVersionStore[String, MetsData](
        DynamoBuilder.buildDynamoConfig(config, namespace = "mets")
      ),
      temporaryCredentialsStore
    )
  }
}
