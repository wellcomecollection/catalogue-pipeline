package uk.ac.wellcome.platform.snapshot_generator.akka.graph

import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.alpakka.s3.{MultipartUploadResult, S3Settings}
import akka.stream.scaladsl.{Broadcast, GraphDSL, RunnableGraph}
import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.display.models.DisplayWork
import uk.ac.wellcome.platform.snapshot_generator.akka.flow.{
  DisplayWorkToJsonStringFlow,
  StringToGzipFlow
}
import uk.ac.wellcome.platform.snapshot_generator.akka.sink.{
  CountingSink,
  S3Sink
}
import uk.ac.wellcome.platform.snapshot_generator.akka.source.DisplayWorkSource
import uk.ac.wellcome.storage.s3.S3ObjectLocation

import scala.concurrent.Future

object UploadSnapshotGraph {
  def apply(
    elasticClient: ElasticClient,
    index: Index,
    s3Settings: S3Settings,
    s3ObjectLocation: S3ObjectLocation)(implicit actorSystem: ActorSystem)
    : RunnableGraph[(Future[Int], Future[MultipartUploadResult])] = {

    // We start with a "source" of display works
    val displayWorkSource = DisplayWorkSource(elasticClient, index)

    // We want to route to both a counter, and to S3
    val countingSink = CountingSink()
    val s3Sink = S3Sink(s3Settings)(s3ObjectLocation)

    RunnableGraph.fromGraph(GraphDSL.create(countingSink, s3Sink)((_, _)) {
      implicit builder => (countingSinkShape, s3SinkShape) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[DisplayWork](outputPorts = 2))

        // The Graph DSL begins here.
        // See: https://doc.akka.io/docs/akka/current/stream/stream-graphs.html

        // "Broadcast" our display works to two "ports"
        // See: https://doc.akka.io/docs/akka/current/stream/operators/Broadcast.html
        displayWorkSource ~> broadcast.in

        // First port sends messages to the countingSink to count the number of works
        broadcast.out(0) ~> countingSinkShape

        // Second port
        broadcast.out(1) ~> (
          // convert display work to string
          DisplayWorkToJsonStringFlow()
        ) ~> (
          // consume strings and gzip bytes
          StringToGzipFlow()
        ) ~> (
          // send those bytes to S3
          s3SinkShape
        )

        // "ClosedShape" indicates that this is the end of the graph and there is a single output.
        // The results of the broadcast via both ports is returned as a tuple when this graph is run.
        ClosedShape
    })
  }
}
