package uk.ac.wellcome.platform.sierra_reader.parsers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

import io.circe.Json
import io.circe.optics.JsonPath.root
import uk.ac.wellcome.models.transformable.sierra.AbstractSierraRecord

object SierraRecordParser {
  def apply[T <: AbstractSierraRecord](
    createRecord: (String, String, Instant) => T)(json: Json): T = {
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
  // deletion as taking precedence.  If it was deleted and then un-deleted
  // TODO: Is that even possible?
  //
  //

  private def getModifiedDate(json: Json): Instant = {
    val maybeUpdatedDate = root.updatedDate.string.getOption(json)

    maybeUpdatedDate match {
      case Some(updatedDate) => Instant.parse(updatedDate)
      case None              => getDeletedAsDatetime(json)
    }
  }

  private def getDeletedAsDatetime(json: Json): Instant = {
    val formatter = DateTimeFormatter.ISO_DATE
    LocalDate
      .parse(root.deletedDate.string.getOption(json).get, formatter)
      .atTime(LocalTime.MAX)
      .toInstant(ZoneOffset.UTC)
  }

  private def getId(json: Json): String =
    root.id.string.getOption(json).get
}
