package weco.pipeline.relation_embedder

import com.sksamuel.elastic4s.Index
import com.typesafe.config.ConfigFactory
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{
  Flow,
  Framing,
  Sink,
  Source,
  StreamConverters
}
import org.apache.pekko.util.ByteString
import weco.elasticsearch.typesafe.ElasticBuilder

import scala.concurrent.{ExecutionContext, Future}
import weco.json.JsonUtil.fromJson
import weco.pipeline.relation_embedder.models.Batch
import weco.json.JsonUtil._
import weco.typesafe.config.builders.EnrichConfig._
//import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object CLIMain extends App {
  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher
  private val config = ConfigFactory.load("application")
  private val stdinSource: Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => System.in)

  private val lineDelimiter: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true
    )
  private val toStringFlow: Flow[ByteString, String, NotUsed] = {
    Flow[ByteString].map(_.utf8String)
  }

  private val relationsService = new PathQueryRelationsService(
    elasticClient = ElasticBuilder.buildElasticClient(config),
    index = Index(config.requireString("es.merged-works.index"))
  )

  private val batchProcessor = new BatchProcessor(
    relationsService: RelationsService,
    bulkWriter = new BulkSTDOutWriter(10, 1.second),
    downstream = STDIODownstream
  )

  private val batchProcessorFlow = Flow.fromFunction(batchProcessor.apply)

  private val toBatchFlow: Flow[String, Batch, NotUsed] =
    Flow.fromFunction((jsonString: String) => fromJson[Batch](jsonString).get)

  private val f = stdinSource
    .via(lineDelimiter)
    .via(toStringFlow)
    .via(toBatchFlow)
    .via(batchProcessorFlow)
    .runWith(Sink.seq)
    .flatMap(Future.sequence(_))
//
//  Await.result(f, 1 minute).andThen {
//    _ =>
//      println("bye")
//      actorSystem.terminate()
//  }

}
