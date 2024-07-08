package weco.pipeline.reindex_worker

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sns.SnsClient
import weco.messaging.sns.{NotificationMessage, SNSIndividualMessageSender}
import weco.messaging.typesafe.SQSBuilder
import weco.pipeline.reindex_worker.config.ReindexJobConfigBuilder
import weco.pipeline.reindex_worker.services.{
  BulkMessageSender,
  RecordReader,
  ReindexWorkerService
}
import weco.typesafe.WellcomeTypesafeApp

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val executionContext: ExecutionContext =
        actorSystem.dispatcher

      implicit val dynamoDBClient: DynamoDbClient =
        DynamoDbClient.builder().build()

      val recordReader = new RecordReader

      val bulkMessageSender = new BulkMessageSender(
        underlying = new SNSIndividualMessageSender(
          snsClient = SnsClient.builder().build()
        )
      )

      new ReindexWorkerService(
        recordReader = recordReader,
        bulkMessageSender = bulkMessageSender,
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        reindexJobConfigMap =
          ReindexJobConfigBuilder.buildReindexJobConfigMap(config)
      )
  }
}
