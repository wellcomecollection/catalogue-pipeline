package weco.catalogue.tei.id_extractor

import scalikejdbc._

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

/** Represents a set of identifiers as stored in MySQL */
case class PathId(
                       path: String,
                       id: String,
                       timeModified: ZonedDateTime
)

object PathId {
  val formatter = DateTimeFormatter.ofPattern("yyyy-M-d k:m:s")
  def apply(p: SyntaxProvider[PathId])(rs: WrappedResultSet): PathId = {
    PathId(
      path = rs.string(p.resultName.path),
      id = rs.string(p.resultName.id),
      timeModified = LocalDateTime.parse(rs.string(p.resultName.timeModified), formatter).atZone(ZoneId.of("Z"))
    )
  }

}
