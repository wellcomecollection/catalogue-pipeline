package uk.ac.wellcome.platform.sierra_reader.services

import java.time.Instant

import akka.Done
import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.syntax._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs._
import uk.ac.wellcome.platform.sierra_reader.config.models.{
  ReaderConfig,
  SierraAPIConfig
}
import uk.ac.wellcome.platform.sierra_reader.flow.SierraRecordWrapperFlow
import uk.ac.wellcome.platform.sierra_reader.models.{
  SierraResourceTypes,
  WindowStatus
}
import uk.ac.wellcome.platform.sierra_reader.sink.SequentialS3Sink
import uk.ac.wellcome.sierra.{SierraSource, ThrottleRate}
import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraBibRecord,
  SierraItemRecord
}
import uk.ac.wellcome.storage.{Identified, ObjectLocation}
import uk.ac.wellcome.storage.s3.S3Config
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class SierraReaderWorkerService(
  sqsStream: SQSStream[NotificationMessage],
  s3client: AmazonS3,
  s3Config: S3Config,
  readerConfig: ReaderConfig,
  sierraAPIConfig: SierraAPIConfig
)(implicit actorSystem: ActorSystem, ec: ExecutionContext)
    extends Logging
    with Runnable {
  implicit val s = s3client
  val windowManager = new WindowManager(
    s3client = s3client,
    s3Config = s3Config,
    readerConfig = readerConfig
  )

  def run(): Future[Done] =
    sqsStream.foreach(
      streamName = this.getClass.getSimpleName,
      process = processMessage
    )

  def processMessage(notificationMessage: NotificationMessage): Future[Unit] =
    for {
      window <- Future.fromTry(
        WindowExtractor.extractWindow(notificationMessage.body)
      )
      windowStatus <- windowManager.getCurrentStatus(window = window)
      _ <- runSierraStream(window = window, windowStatus = windowStatus)
    } yield ()

  private def runSierraStream(
    window: String,
    windowStatus: WindowStatus): Future[Identified[ObjectLocation, String]] = {

    info(s"Running the stream with window=$window and status=$windowStatus")

    val baseParams =
      Map("updatedDate" -> window, "fields" -> readerConfig.fields)
    val params = windowStatus.id match {
      case Some(id) => baseParams ++ Map("id" -> s"[$id,]")
      case None     => baseParams
    }

    val s3sink = SequentialS3Sink(
      store = S3TypedStore[String],
      bucketName = s3Config.bucketName,
      keyPrefix = windowManager.buildWindowShard(window),
      offset = windowStatus.offset
    )

    val sierraSource = SierraSource(
      apiUrl = sierraAPIConfig.apiURL,
      oauthKey = sierraAPIConfig.oauthKey,
      oauthSecret = sierraAPIConfig.oauthSec,
      throttleRate = ThrottleRate(3, per = 1.second),
      timeoutMs = 60000
    )(resourceType = readerConfig.resourceType.toString, params)

    val outcome = sierraSource
      .via(SierraRecordWrapperFlow(createRecord))
      .grouped(readerConfig.batchSize)
      .map(recordBatch => toJson(recordBatch))
      .zipWithIndex
      .runWith(s3sink)

    // This serves as a marker that the window is complete, so we can audit
    // our S3 bucket to see which windows were never successfully completed.
    outcome.flatMap { _ =>
      val key =
        s"windows_${readerConfig.resourceType.toString}_complete/${windowManager
          .buildWindowLabel(window)}"

      Future.fromTry(
        S3TypedStore[String]
          .put(ObjectLocation(s3Config.bucketName, key))("")
          .left
          .map { _.e }
          .toTry)
    }
  }

  private def createRecord: (String, String, Instant) => AbstractSierraRecord =
    readerConfig.resourceType match {
      case SierraResourceTypes.bibs  => SierraBibRecord.apply
      case SierraResourceTypes.items => SierraItemRecord.apply
    }

  private def toJson(records: Seq[AbstractSierraRecord]): Json =
    readerConfig.resourceType match {
      case SierraResourceTypes.bibs =>
        records.asInstanceOf[Seq[SierraBibRecord]].asJson
      case SierraResourceTypes.items =>
        records.asInstanceOf[Seq[SierraItemRecord]].asJson
    }
}
