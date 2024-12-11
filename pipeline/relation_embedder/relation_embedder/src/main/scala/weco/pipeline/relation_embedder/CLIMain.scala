package weco.pipeline.relation_embedder

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Sink}

import com.typesafe.config.ConfigFactory
import com.sksamuel.elastic4s.Index

import weco.typesafe.config.builders.EnrichConfig._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.json.JsonUtil._
import weco.pipeline.relation_embedder.lib.StdInBatches

/** A main function providing a local CLI for the relation embedder. To invoke,
  * provide a list of Batch objects in NDJSON on StdIn.
  *
  * This will embed relations as required and print the resulting Works and
  * their identifiers
  */
object CLIMain extends App with StdInBatches {
  implicit val actorSystem: ActorSystem = ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext = actorSystem.dispatcher

  private val config = ConfigFactory.load("application")
  private val esClient = ElasticBuilder.buildElasticClient(config)

  private val batchProcessor = new BatchProcessor(
    relationsService = new PathQueryRelationsService(
      elasticClient = esClient,
      index = Index(config.requireString("es.merged-works.index"))
    ),
    bulkWriter = new BulkSTDOutWriter(10),
    downstream = STDIODownstream
  )

  private val batchProcessorFlow = Flow.fromFunction(batchProcessor.apply)

  Await.result(
    batchSource
      .via(batchProcessorFlow)
      .runWith(Sink.seq)
      .flatMap(Future.sequence(_)),
    // give the futures a chance to resolve before shutting down
    5 minutes
  )
  esClient.close()
  actorSystem.terminate()
}
