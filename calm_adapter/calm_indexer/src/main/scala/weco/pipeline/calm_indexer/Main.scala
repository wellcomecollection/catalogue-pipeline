package weco.pipeline.calm_indexer

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.sksamuel.elastic4s.{ElasticClient, Index}
import weco.catalogue.source_model.calm.CalmRecord
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.json.JsonUtil._
import weco.messaging.typesafe.SQSBuilder
import weco.storage.store.s3.S3TypedStore
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline.calm_indexer.services.Worker

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()

    implicit val elasticClient: ElasticClient =
      ElasticBuilder.buildElasticClient(config)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client

    new Worker(
      sqsStream = SQSBuilder.buildSQSStream(config),
      calmReader = S3TypedStore[CalmRecord],
      index = Index(config.requireString("es.index"))
    )
  }
}
