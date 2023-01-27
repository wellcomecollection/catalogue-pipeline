package weco.pipeline.sierra_reader.services

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import grizzled.slf4j.Logging
import software.amazon.awssdk.services.s3.S3Client
import weco.json.JsonUtil._
import weco.pipeline.sierra_reader.config.models.ReaderConfig
import weco.pipeline.sierra_reader.exceptions.SierraReaderException
import weco.storage.Identified
import weco.storage.listing.s3.S3ObjectLocationListing
import weco.storage.s3.{S3Config, S3ObjectLocation, S3ObjectLocationPrefix}
import weco.storage.store.s3.S3TypedStore
import weco.pipeline.sierra_reader.models.WindowStatus
import weco.sierra.models.identifiers.UntypedSierraRecordNumber

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class WindowManager(
  s3Config: S3Config,
  readerConfig: ReaderConfig
)(implicit s3Client: S3Client)
    extends Logging {

  private val listing = S3ObjectLocationListing()
  private val store = S3TypedStore[String]

  def getCurrentStatus(window: String): Future[WindowStatus] = Future.fromTry {
    val prefix = S3ObjectLocationPrefix(
      bucket = s3Config.bucketName,
      keyPrefix = buildWindowShard(window)
    )

    info(
      s"Searching for records from previous invocation of the reader in $prefix"
    )

    for {
      lastExistingKey: Option[S3ObjectLocation] <- Try {
        listing.list(prefix) match {
          case Right(result) => result.lastOption
          case Left(err)     => throw err.e
        }
      }

      status <- lastExistingKey match {
        case Some(location) =>
          debug(s"Found JSON file from previous run in S3: $location")
          getStatusFromLastKey(location)

        case None =>
          debug(s"No existing records found in S3; starting from scratch")
          Success(WindowStatus(id = None, offset = 0))
      }
    } yield status
  }

  private def getStatusFromLastKey(
    location: S3ObjectLocation
  ): Try[WindowStatus] = {
    // Our SequentialS3Sink creates filenames that end 0000.json, 0001.json, ..., with an optional prefix.
    // Find the number on the end of the last file.
    val embeddedIndexMatch = "(\\d{4})\\.json$".r.unanchored

    for {
      offset <- location.key match {
        case embeddedIndexMatch(index) => Success(index.toInt)
        case _ =>
          Failure(
            SierraReaderException(s"Unable to determine offset in $location")
          )
      }

      latestBody <- store.get(location) match {
        case Right(Identified(_, body)) => Success(body)
        case Left(err)                  => Failure(err.e)
      }

      latestId <- getLatestId(location, latestBody)

      _ = info(s"Found latest ID in S3: $latestId")

      newId = (latestId.toInt + 1).toString
      windowStatus = WindowStatus(id = newId, offset = offset + 1)
    } yield windowStatus
  }

  def buildWindowShard(window: String) =
    s"records_${readerConfig.recordType.toString}/${buildWindowLabel(window)}/"

  def buildWindowLabel(window: String) = {
    // Window is a string like [2013-12-01T01:01:01+00:00,2013-12-01T01:01:01+00:00].
    // We discard the square braces, colons and comma so we get slightly nicer filenames.
    val dateTimes = window
      .replaceAll("\\[", "")
      .replaceAll("\\]", "")
      .split(",")

    dateTimes
      .map(dateTime => {
        val accessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(dateTime)
        val instant = Instant.from(accessor).atOffset(ZoneOffset.UTC)
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH-mm-ssX").format(instant)
      })
      .mkString("__")
  }

  // The contents of our S3 files should be an array of either SierraBibRecord
  // or SierraItemRecord; we want to get the last ID of the current contents
  // so we know what to ask the Sierra API for next.
  //
  private def getLatestId(
    location: S3ObjectLocation,
    s3contents: String
  ): Try[String] = {
    case class Identified(id: UntypedSierraRecordNumber)

    fromJson[List[Identified]](s3contents) match {
      case Success(ids) =>
        val lastId = ids.map { _.id.withoutCheckDigit }.sorted.lastOption

        lastId match {
          case Some(id) => Success(id)
          case None =>
            Failure(
              SierraReaderException(s"JSON at $location did not contain an id")
            )
        }

      case Failure(_) =>
        Failure(
          SierraReaderException(
            s"S3 object at $location could not be parsed as JSON"
          )
        )
    }
  }
}
