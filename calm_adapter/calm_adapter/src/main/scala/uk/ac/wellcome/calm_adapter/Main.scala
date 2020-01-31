package uk.ac.wellcome.calm_adapter

import scala.concurrent.{Future, ExecutionContext}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}

object Main extends WellcomeTypesafeApp {

  runWithConfig { config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new CalmAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "CALM adapter"),
      new CalmRetriever {
        def getRecords(query: CalmQuery): Future[List[CalmRecord]] =
          ???
      }
    )
  }
}
