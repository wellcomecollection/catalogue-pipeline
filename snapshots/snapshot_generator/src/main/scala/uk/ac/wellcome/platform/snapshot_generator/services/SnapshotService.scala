package uk.ac.wellcome.platform.snapshot_generator.services

import java.time.Instant

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.s3.{MultipartUploadResult, ObjectMetadata, S3Attributes, S3Settings}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Broadcast, GraphDSL, RunnableGraph, Sink, Source}
import akka.util.ByteString
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticClient
import grizzled.slf4j.Logging
import uk.ac.wellcome.display.models.{DisplayWork, _}
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.snapshot_generator.flow.{DisplayWorkToJsonStringFlow, IdentifiedWorkToVisibleDisplayWork, StringToGzipFlow}
import uk.ac.wellcome.platform.snapshot_generator.models.{CompletedSnapshotJob, SnapshotJob, SnapshotResult}
import uk.ac.wellcome.platform.snapshot_generator.source.ElasticsearchWorksSource
import WorkState.Identified
import akka.stream.ClosedShape
import uk.ac.wellcome.storage.s3.S3ObjectLocation

class SnapshotService(akkaS3Settings: S3Settings,
                      elasticClient: ElasticClient,
                      elasticConfig: ElasticConfig)(
  implicit actorSystem: ActorSystem,
  ec: ExecutionContext
) extends Logging {

  val s3Endpoint = akkaS3Settings.endpointUrl.getOrElse("s3:/")

  def buildLocation(bucketName: String, objectKey: String): Uri =
    Uri(s"$s3Endpoint/$bucketName/$objectKey")

  def generateSnapshot(
    snapshotJob: SnapshotJob): Future[CompletedSnapshotJob] = {
    info(s"ConvertorService running $snapshotJob")

    val startedAt = Instant.now

    val (documentCountFuture, uploadResultFuture) = snapshotJob.apiVersion match {
      case ApiVersions.v2 =>
        uploadSnapshot(
          location = snapshotJob.s3Location,
          index = elasticConfig.worksIndex,
          toDisplayWork = DisplayWork(_, WorksIncludes.includeAll())
        )
    }

    for {
      uploadResult <- uploadResultFuture
      documentCount <- documentCountFuture
      objectMetadata <- getObjectMetadata(
        location = snapshotJob.s3Location
      )

      snapshotResult = SnapshotResult(
        indexName = elasticConfig.worksIndex.name,
        documentCount = documentCount,
        displayModel = Work.getClass.getCanonicalName,
        startedAt = startedAt,
        finishedAt = Instant.now(),
        s3Etag = uploadResult.etag,
        s3Size = objectMetadata.contentLength,
        s3Location = snapshotJob.s3Location
      )

    } yield CompletedSnapshotJob(
      snapshotJob = snapshotJob,
      snapshotResult = snapshotResult
    )
  }

  private def getObjectMetadata(location: S3ObjectLocation): Future[ObjectMetadata] = {
    val objectMetadataFlow = S3
      .getObjectMetadata(bucket = location.bucket, key = location.key)
      .withAttributes(S3Attributes.settings(akkaS3Settings))
      .map {
        case Some(objectMetadata) => objectMetadata
        case None =>
          val runtimeException = new RuntimeException(
            s"No object found at $location"
          )

          error(
            msg = "Failed getting ObjectMetadata!",
            t = runtimeException
          )

          throw runtimeException
      }

    objectMetadataFlow.runWith(Sink.head)
  }

  private def uploadSnapshot(location: S3ObjectLocation,
                        index: Index,
                        toDisplayWork: Work.Visible[Identified] => DisplayWork)
  : (Future[Int], Future[MultipartUploadResult]) = {

    val displayWorks: Source[DisplayWork, Any] =
      ElasticsearchWorksSource(elasticClient = elasticClient, index = index)
        .via(IdentifiedWorkToVisibleDisplayWork(toDisplayWork))

    val s3Sink: Sink[ByteString, Future[MultipartUploadResult]] =
      S3.multipartUpload(
          bucket = location.bucket,
          key = location.key
        )
        .withAttributes(S3Attributes.settings(akkaS3Settings))

    val countingSink: Sink[DisplayWork, Future[Int]] =
      Sink.fold[Int, DisplayWork](0)((acc, _) => acc + 1)

    val graph = RunnableGraph.fromGraph(GraphDSL.create(countingSink, s3Sink)((_, _)) { implicit builder =>
      (countingSinkShape, s3SinkShape) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[DisplayWork](2))

        displayWorks ~> broadcast.in
        broadcast.out(0) ~> countingSinkShape
        broadcast.out(1) ~> DisplayWorkToJsonStringFlow.flow ~> StringToGzipFlow() ~> s3SinkShape
        ClosedShape
    })

    graph.run()
  }
}
