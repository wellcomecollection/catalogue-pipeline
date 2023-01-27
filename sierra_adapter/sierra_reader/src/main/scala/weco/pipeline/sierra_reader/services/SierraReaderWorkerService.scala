package weco.pipeline.sierra_reader.services

import akka.Done
import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.syntax._
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import weco.catalogue.source_model.sierra._
import weco.catalogue.source_model.Implicits._
import weco.http.client.HttpGet
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs._
import weco.pipeline.sierra_reader.config.models.ReaderConfig
import weco.pipeline.sierra_reader.flow.SierraRecordWrapperFlow
import weco.pipeline.sierra_reader.models.WindowStatus
import weco.pipeline.sierra_reader.sink.SequentialS3Sink
import weco.pipeline.sierra_reader.source.{SierraSource, ThrottleRate}
import weco.sierra.models.identifiers.SierraRecordTypes
import weco.storage.s3.S3Config
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.Runnable

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class SierraReaderWorkerService(
  client: HttpGet,
  sqsStream: SQSStream[NotificationMessage],
  s3Config: S3Config,
  readerConfig: ReaderConfig
)(implicit actorSystem: ActorSystem, ec: ExecutionContext, s3Client: S3Client)
    extends Logging
    with Runnable {
  val windowManager = new WindowManager(
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
    windowStatus: WindowStatus
  ): Future[Unit] = {
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
      client = client,
      throttleRate = ThrottleRate(3, per = 1.second)
    )(recordType = readerConfig.recordType, params)

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
        s"windows_${readerConfig.recordType.toString}_complete/${windowManager
            .buildWindowLabel(window)}"

      val putRequest =
        PutObjectRequest
          .builder()
          .bucket(s3Config.bucketName)
          .key(key)
          .build()

      val requestBody = RequestBody.empty()

      Future { s3Client.putObject(putRequest, requestBody) }
    }
  }

  private def createRecord
    : (String, String, Instant) => AbstractSierraRecord[_] =
    readerConfig.recordType match {
      case SierraRecordTypes.bibs     => SierraBibRecord.apply
      case SierraRecordTypes.items    => SierraItemRecord.apply
      case SierraRecordTypes.holdings => SierraHoldingsRecord.apply
      case SierraRecordTypes.orders   => SierraOrderRecord.apply
    }

  private def toJson(records: Seq[AbstractSierraRecord[_]]): Json =
    readerConfig.recordType match {
      case SierraRecordTypes.bibs =>
        records.asInstanceOf[Seq[SierraBibRecord]].asJson
      case SierraRecordTypes.items =>
        records.asInstanceOf[Seq[SierraItemRecord]].asJson
      case SierraRecordTypes.holdings =>
        records.asInstanceOf[Seq[SierraHoldingsRecord]].asJson
      case SierraRecordTypes.orders =>
        records.asInstanceOf[Seq[SierraOrderRecord]].asJson
    }
}
