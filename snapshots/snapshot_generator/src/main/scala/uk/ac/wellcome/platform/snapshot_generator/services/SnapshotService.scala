package uk.ac.wellcome.platform.snapshot_generator.services

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.s3.S3Settings
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticClient
import grizzled.slf4j.Logging
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.platform.snapshot_generator.akkastreams.graph.UploadSnapshotGraph
import uk.ac.wellcome.platform.snapshot_generator.akkastreams.source.S3ObjectMetadataSource
import uk.ac.wellcome.platform.snapshot_generator.models.{
  CompletedSnapshotJob,
  SnapshotJob,
  SnapshotResult
}
import uk.ac.wellcome.storage.s3.S3ObjectLocation

import scala.concurrent.{ExecutionContext, Future}

class SnapshotService(akkaS3Settings: S3Settings,
                      elasticClient: ElasticClient,
                      elasticConfig: ElasticConfig)(
  implicit actorSystem: ActorSystem,
  ec: ExecutionContext
) extends Logging {

  val s3Endpoint: String = akkaS3Settings.endpointUrl.getOrElse("s3:/")

  def buildLocation(s3Location: S3ObjectLocation): Uri =
    Uri(s"$s3Endpoint/${s3Location.bucket}/${s3Location.key}")

  def generateSnapshot(
    snapshotJob: SnapshotJob): Future[CompletedSnapshotJob] = {
    info(msg = s"${this.getClass.getSimpleName} running $snapshotJob")

    val startedAt = Instant.now

    val (documentCountFuture, uploadResultFuture) =
      snapshotJob.apiVersion match {
        case ApiVersions.v2 =>
          UploadSnapshotGraph(
            elasticClient = elasticClient,
            index = elasticConfig.worksIndex,
            s3Settings = akkaS3Settings,
            s3ObjectLocation = snapshotJob.s3Location
          ).run()
      }

    for {
      uploadResult <- uploadResultFuture
      documentCount <- documentCountFuture

      objectMetadata <- S3ObjectMetadataSource(
        s3ObjectLocation = snapshotJob.s3Location,
        s3Settings = akkaS3Settings,
      ).runWith(Sink.head)

      snapshotResult = SnapshotResult(
        indexName = elasticConfig.worksIndex.name,
        documentCount = documentCount,
        displayModel = DisplayWork.getClass.getCanonicalName,
        startedAt = startedAt,
        finishedAt = Instant.now(),
        s3Etag = uploadResult.etag,
        s3Size = objectMetadata.contentLength,
        s3Location = snapshotJob.s3Location
      )

    } yield
      CompletedSnapshotJob(
        snapshotJob = snapshotJob,
        snapshotResult = snapshotResult
      )
  }
}
