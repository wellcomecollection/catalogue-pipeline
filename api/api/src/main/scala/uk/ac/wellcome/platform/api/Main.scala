package uk.ac.wellcome.platform.api

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import co.elastic.apm.attach.ElasticApmAttacher
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.DisplayElasticConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.platform.api.models.ApiConfig
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.typesafe.{Runnable, WellcomeTypesafeApp}

import scala.concurrent.{ExecutionContext, Promise}

object Main extends WellcomeTypesafeApp {

  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    ElasticApmAttacher.attach()
    val elasticClient = ElasticBuilder.buildElasticClient(config)

    val elasticConfig =
      DisplayElasticConfig(indexV2 = Index(config.required("es.index.v2")))

    val apiConfig =
      ApiConfig(
        host = config.getOrElse[String]("api.host")(default = "localhost"),
        scheme = config.getOrElse[String]("api.scheme")(default = "https"),
        defaultPageSize = config.getOrElse[Int]("api.pageSize")(default = 10),
        pathPrefix =
          s"${config.getOrElse[String]("api.apiName")(default = "catalogue")}",
        contextSuffix = config.getOrElse[String]("api.context.suffix")(
          default = "context.json"),
      )

    val router = new Router(elasticClient, elasticConfig, apiConfig)

    new Runnable {
      def run() =
        Http()
          .bindAndHandle(router.routes, "0.0.0.0", 8888)
          .flatMap(_ => Promise[Done].future)
    }
  }
}
