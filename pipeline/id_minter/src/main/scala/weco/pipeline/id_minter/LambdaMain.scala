package weco.pipeline.id_minter

import com.sksamuel.elastic4s.Index
import io.circe.Json
import org.apache.pekko.actor.ActorSystem
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.lambda.SQSLambdaApp
import weco.pipeline.id_minter.config.models.{
  IdMinterConfig,
  IdMinterConfigurable
}
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.catalogue.internal_model.Implicits._
import weco.pipeline.id_minter.database.RDSIdentifierGenerator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class LambdaMain
    extends SQSLambdaApp[String, Seq[String], IdMinterConfig]
    with IdMinterConfigurable {

  private val identifierGenerator = RDSIdentifierGenerator(
    config.rdsClientConfig,
    config.identifiersTableConfig
  )

  private val esClient = ElasticBuilder.buildElasticClient(config.elasticConfig)

  private val workIndexer =
    new ElasticIndexer[Work[Identified]](
      client = esClient,
      index = Index(config.targetIndex)
    )

  private val jsonRetriever = new ElasticSourceRetriever[Json](
    client = esClient,
    index = Index(config.sourceIndex)
  )

  private val minter = new IdMinter(identifierGenerator)
  override def processT(t: List[String]): Future[Seq[String]] = {
    implicit val ec: ExecutionContext =
      ActorSystem("main-actor-system").dispatcher

    val x =
      jsonRetriever(t)
        .map {
          result => result.found.values.map(minter.processJson)
        }
        .flatMap {
          maybeWorks: Iterable[Try[Work[Identified]]] =>
            storeWorks(maybeWorks.toSeq)
        }
        .map {
          case Left(value) => value.map(_.sourceIdentifier.value)
          case Right(_)    => Nil
        }
    x
  }

  private def storeWorks(maybeWorks: Seq[Try[Work[Identified]]]) = {
    workIndexer(maybeWorks.collect {
      case Success(value) => value
    })
  }
}
