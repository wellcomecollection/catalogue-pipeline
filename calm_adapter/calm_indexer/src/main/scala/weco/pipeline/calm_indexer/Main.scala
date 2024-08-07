package weco.pipeline.calm_indexer

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.{ElasticClient, Index}
import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.Implicits._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SQSBuilder
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline.calm_indexer.services.Worker

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      implicit val elasticClient: ElasticClient =
        ElasticBuilder.buildElasticClient(config)

      implicit val s3Client: S3Client = S3Client.builder().build()

      new Worker(
        sqsStream = SQSBuilder.buildSQSStream(config),
        calmReader = S3TypedStore[CalmRecord],
        index = Index(config.requireString("es.index"))
      )
  }
}
