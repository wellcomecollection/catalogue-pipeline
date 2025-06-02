package weco.pipeline.relation_embedder

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.sksamuel.elastic4s.Index
import weco.typesafe.config.builders.EnrichConfig._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.json.JsonUtil._
import weco.lambda.{STDIODownstream, StdInNDJSON}
import weco.pipeline.relation_embedder.models.Batch

/** A main function providing a local CLI for the relation embedder. To invoke,
  * provide a list of Batch objects in NDJSON on StdIn.
  *
  * This will embed relations as required and print the resulting Works and
  * their identifiers
  */
object CLIMain extends App with StdInNDJSON[Batch] {
  implicit val actorSystem: ActorSystem = ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext = actorSystem.dispatcher

  private val config = ConfigFactory.load("application")
  private val esClient = ElasticBuilder.buildElasticClient(config)

  private val batchProcessor = new BatchProcessor(
    relationsService = new PathQueryRelationsService(
      elasticClient = esClient,
      index = Index(config.requireString("es.denormalised-works.index"))
    ),
    bulkWriter = new BulkSTDOutWriter(10),
    downstream = STDIODownstream
  )

  Await.result(
    Future.sequence(instances.map(batchProcessor.apply)),
    5 minutes
  )

  esClient.close()
  actorSystem.terminate()
}
