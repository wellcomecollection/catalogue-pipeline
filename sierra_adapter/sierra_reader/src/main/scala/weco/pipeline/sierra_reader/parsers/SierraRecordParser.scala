package weco.pipeline.sierra_reader.parsers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}
import io.circe.Json
import io.circe.optics.JsonPath.root
import weco.catalogue.source_model.json.JsonOps._
import weco.catalogue.source_model.sierra.AbstractSierraRecord

object SierraRecordParser {
  def apply[T <: AbstractSierraRecord[_]](
    createRecord: (String, String, Instant) => T
  )(json: Json): T = {
    val id = getId(json)
    val data = json.noSpaces
    val modifiedDate = getModifiedDate(json)

    createRecord(id, data, modifiedDate)
  }

  // The Sierra API has different levels of granularity for updated/deleted values.
  //
  //  - "updatedDate" is a date and a time, e.g. "2013-12-11T19:20:28Z"
  //  - "deletedDate" is just a date, e.g. "2014-01-11"
  //
  // We need to be able to compare these two dates, so we can order records
  // consistently.
  //
  // If a record was deleted and modified on the same day, we should treat the
  // deletion as taking precedence.  After a record is deleted in Sierra, it
  // cannot be un-deleted.
  //
  private def getModifiedDate(json: Json): Instant = {
    val maybeUpdatedDate = root.updatedDate.string.getOption(json)

    maybeUpdatedDate match {
      case Some(updatedDate) => getUpdatedAsDateTime(updatedDate)
      case None              => getDeletedAsDatetime(json)
    }
  }

  private def getUpdatedAsDateTime(updatedDate: String): Instant =
    Instant.parse(updatedDate)

  private def getDeletedAsDatetime(json: Json): Instant = {
    val formatter = DateTimeFormatter.ISO_DATE
    LocalDate
      .parse(root.deletedDate.string.getOption(json).get, formatter)
      .atTime(LocalTime.MAX)
      .toInstant(ZoneOffset.UTC)
  }

  private def getId(json: Json): String =
    root.id
      .as[StringOrInt]
      .getOption(json)
      .map { _.underlying }
      .get
}
