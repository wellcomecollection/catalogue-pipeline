package weco.pipeline.sierra_reader.services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import weco.pipeline.sierra_reader.exceptions.SierraReaderException

import scala.util.Try

object WindowExtractor extends Logging {
  private val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

  def extractWindow(jsonString: String): Try[String] =
    Try(parse(jsonString).right.get)
      .map { json =>
        val start = extractField("start", json)
        val end = extractField("end", json)

        val startDateTime = parseStringToDateTime(start)
        val endDateTime = parseStringToDateTime(end)
        if (
          startDateTime
            .isAfter(endDateTime) || startDateTime.isEqual(endDateTime)
        )
          throw new Exception(s"$start must be before $end")

        s"[$start,$end]"
      }
      .recover { case e: Exception =>
        warn(s"Error parsing $jsonString", e)
        throw SierraReaderException(e)
      }

  private def extractField(field: String, json: Json): String =
    root.selectDynamic(field).string.getOption(json).get

  private def parseStringToDateTime(dateTimeString: String): LocalDateTime =
    LocalDateTime.parse(dateTimeString, formatter)
}
