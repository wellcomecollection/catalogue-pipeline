package uk.ac.wellcome.platform.sierra_reader.services

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import com.amazonaws.services.s3.AmazonS3
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.sierra_reader.config.models.ReaderConfig
import uk.ac.wellcome.platform.sierra_reader.exceptions.SierraReaderException
import uk.ac.wellcome.platform.sierra_reader.models.WindowStatus
import uk.ac.wellcome.storage.listing.s3.S3ObjectLocationListing
import uk.ac.wellcome.storage.s3.{S3Config, S3ObjectLocation, S3ObjectLocationPrefix}
import weco.catalogue.sierra_adapter.models.UntypedSierraRecordNumber

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class WindowManager(
  s3Config: S3Config,
  readerConfig: ReaderConfig
)(implicit ec: ExecutionContext, s3Client: AmazonS3)
    extends Logging {

  private val listing = S3ObjectLocationListing()

  def getCurrentStatus(window: String): Future[WindowStatus] = {
    val prefix = S3ObjectLocationPrefix(
      bucket = s3Config.bucketName,
      keyPrefix = buildWindowShard(window)
    )

    info(s"Searching for records from previous invocation of the reader in $prefix")

    for {
      lastExistingKey: Option[S3ObjectLocation] <- Future {
        listing.list(prefix) match {
          case Right(result) => result.lastOption
          case Left(err) => throw err.e
        }
      }

      status <- Future {
        lastExistingKey match {
          case Some(location) =>
            debug(s"Found JSON file from previous run in S3: $location")
            getStatusFromLastKey(location)

          case None =>
            debug(s"No existing records found in S3; starting from scratch")
            WindowStatus(id = None, offset = 0)
        }
      }
    } yield status
  }

  private def getStatusFromLastKey(location: S3ObjectLocation): WindowStatus = {
    // Our SequentialS3Sink creates filenames that end 0000.json, 0001.json, ..., with an optional prefix.
    // Find the number on the end of the last file.
    val embeddedIndexMatch = "(\\d{4})\\.json$".r.unanchored
    val offset = location.key match {
      case embeddedIndexMatch(index) => index.toInt
      case _ =>
        throw SierraReaderException(s"Unable to determine offset in $location")
    }

    val lastBody =
      scala.io.Source
        .fromInputStream(
          s3Client.getObject(location.bucket, location.key).getObjectContent
        )
        .mkString

    val maybeLastId = getLastId(lastBody)

    info(s"Found latest ID in S3: $maybeLastId")

    maybeLastId match {
      case Some(id) =>
        val newId = (id.toInt + 1).toString
        WindowStatus(id = newId, offset = offset + 1)
      case None =>
        throw SierraReaderException(
          s"JSON <<$lastBody>> did not contain an id")
    }
  }

  def buildWindowShard(window: String) =
    s"records_${readerConfig.resourceType.toString}/${buildWindowLabel(window)}/"

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
  private def getLastId(s3contents: String): Option[String] = {
    case class Identified(id: UntypedSierraRecordNumber)

    fromJson[List[Identified]](s3contents) match {
      case Success(ids) =>
        ids
          .map { _.id.withoutCheckDigit }
          .sorted
          .lastOption
      case Failure(_) =>
        throw SierraReaderException(
          s"S3 contents <<$s3contents> could not be parsed as JSON")
    }
  }
}
