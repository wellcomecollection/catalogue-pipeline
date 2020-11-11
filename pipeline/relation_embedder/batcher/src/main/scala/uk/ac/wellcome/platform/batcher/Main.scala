package uk.ac.wellcome.platform.router

// import akka.actor.ActorSystem
import com.typesafe.config.Config

// import uk.ac.wellcome.messaging.sns.NotificationMessage
// import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
// import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
// import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

// import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    // implicit val actorSystem: ActorSystem =
    //   AkkaBuilder.buildActorSystem()
    // implicit val executionContext: ExecutionContext =
    //   AkkaBuilder.buildExecutionContext()

    ???

    // new RouterWorkerService(
    //   sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
    //   worksMsgSender = SNSBuilder
    //     .buildSNSMessageSender(
    //       config,
    //       namespace = "work-sender",
    //       subject = "Sent from the router"),
    //   pathsMsgSender = SNSBuilder
    //     .buildSNSMessageSender(
    //       config,
    //       namespace = "path-sender",
    //       subject = "Sent from the router"),
    //   workRetriever = new ElasticRetriever(esClient, mergedIndex)
    // )
  }
}
