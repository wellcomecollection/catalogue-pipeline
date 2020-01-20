package uk.ac.wellcome.platform.sierra_reader.parsers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}

import io.circe.Json
import io.circe.optics.JsonPath.root
import uk.ac.wellcome.models.transformable.sierra.AbstractSierraRecord

object SierraRecordParser {
  def apply[T <: AbstractSierraRecord](createRecord: (String, String, Instant) => T)(json: Json): T = {
    val id = getId(json)
    val data = json.noSpaces
    val modifiedDate = getModifiedDate(json)

    createRecord(id, data, modifiedDate)
  }

  private def getModifiedDate(json: Json): Instant = {
    val maybeUpdatedDate = root.updatedDate.string.getOption(json)

    maybeUpdatedDate match {
      case Some(updatedDate) => Instant.parse(updatedDate)
      case None              => getDeletedDateTimeAtStartOfDay(json)
    }
  }

  private def getDeletedDateTimeAtStartOfDay(json: Json): Instant = {
    val formatter = DateTimeFormatter.ISO_DATE
    LocalDate
      .parse(root.deletedDate.string.getOption(json).get, formatter)
      .atStartOfDay()
      .toInstant(ZoneOffset.UTC)
  }

  private def getId(json: Json): String =
    root.id.string.getOption(json).get
}
