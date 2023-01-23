package weco.pipeline.sierra_indexer

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticClient
import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.Implicits._
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SQSBuilder
import weco.pipeline.sierra_indexer.services.Worker
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")
    implicit val ec: ExecutionContext =
      actorSystem.dispatcher

    implicit val elasticClient: ElasticClient =
      ElasticBuilder.buildElasticClient(config)

    implicit val s3Client: S3Client = S3Client.builder().build()

    new Worker(
      sqsStream = SQSBuilder.buildSQSStream(config),
      sierraReadable = S3TypedStore[SierraTransformable]
    )
  }
}
