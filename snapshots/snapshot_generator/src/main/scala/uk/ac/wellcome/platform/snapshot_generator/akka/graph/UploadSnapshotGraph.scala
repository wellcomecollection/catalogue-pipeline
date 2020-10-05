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
    : RunnableGraph[(Future[Int], Future[MultipartUploadResult])] =
    RunnableGraph.fromGraph(
      GraphDSL.create(CountingSink(), S3Sink(s3Settings)(s3ObjectLocation))(
        (_, _)) { implicit builder => (countingSinkShape, s3SinkShape) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[DisplayWork](outputPorts = 2))

        DisplayWorkSource(elasticClient, index) ~> broadcast.in
        broadcast.out(0) ~> countingSinkShape
        broadcast
          .out(1) ~> DisplayWorkToJsonStringFlow() ~> StringToGzipFlow() ~> s3SinkShape
        ClosedShape
      })
}
