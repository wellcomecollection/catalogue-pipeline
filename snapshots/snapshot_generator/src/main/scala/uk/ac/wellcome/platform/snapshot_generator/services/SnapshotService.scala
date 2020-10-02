package uk.ac.wellcome.platform.snapshot_generator.services

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.s3.{MultipartUploadResult, S3Attributes, S3Settings}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticClient
import grizzled.slf4j.Logging
import uk.ac.wellcome.display.models.{DisplayWork, _}
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.snapshot_generator.flow.{
  DisplayWorkToJsonStringFlow,
  IdentifiedWorkToVisibleDisplayWork,
  StringToGzipFlow
}
import uk.ac.wellcome.platform.snapshot_generator.models.{
  CompletedSnapshotJob,
  SnapshotJob
}
import uk.ac.wellcome.platform.snapshot_generator.source.ElasticsearchWorksSource
import WorkState.Identified
import uk.ac.wellcome.storage.s3.S3ObjectLocation

class SnapshotService(akkaS3Settings: S3Settings,
                      elasticClient: ElasticClient,
                      elasticConfig: ElasticConfig)(
  implicit actorSystem: ActorSystem,
  ec: ExecutionContext
) extends Logging {

  val s3Endpoint = akkaS3Settings.endpointUrl.getOrElse("s3:/")

  def buildLocation(s3Location: S3ObjectLocation): Uri =
    Uri(s"$s3Endpoint/${s3Location.bucket}/${s3Location.key}")

  def generateSnapshot(
    snapshotJob: SnapshotJob): Future[CompletedSnapshotJob] = {
    info(s"ConvertorService running $snapshotJob")

    val uploadResult = snapshotJob.apiVersion match {
      case ApiVersions.v2 =>
        runStream(
          s3Location = snapshotJob.s3Location,
          index = elasticConfig.worksIndex,
          toDisplayWork = DisplayWork(_, WorksIncludes.includeAll())
        )
    }

    uploadResult.map { _ =>
      val targetLocation = buildLocation(snapshotJob.s3Location)

      CompletedSnapshotJob(
        snapshotJob = snapshotJob,
        targetLocation = targetLocation
      )
    }
  }

  private def runStream(s3Location: S3ObjectLocation,
                        index: Index,
                        toDisplayWork: Work.Visible[Identified] => DisplayWork)
    : Future[MultipartUploadResult] = {

    // This source outputs DisplayWorks in the elasticsearch index.
    val displayWorks: Source[DisplayWork, Any] =
      ElasticsearchWorksSource(elasticClient = elasticClient, index = index)
        .via(IdentifiedWorkToVisibleDisplayWork(toDisplayWork))

    // This source generates JSON strings of DisplayWork instances, which
    // should be written to the destination snapshot.
    val jsonStrings: Source[String, Any] = displayWorks
      .via(DisplayWorkToJsonStringFlow.flow)

    // This source generates gzip-compressed JSON strings, corresponding to
    // the DisplayWork instances from the source snapshot.
    val gzipContent: Source[ByteString, Any] = jsonStrings
      .via(StringToGzipFlow())

    val s3Sink: Sink[ByteString, Future[MultipartUploadResult]] =
      S3.multipartUpload(
          bucket = s3Location.bucket,
          key = s3Location.key
        )
        .withAttributes(S3Attributes.settings(akkaS3Settings))

    gzipContent.runWith(s3Sink)
  }
}
