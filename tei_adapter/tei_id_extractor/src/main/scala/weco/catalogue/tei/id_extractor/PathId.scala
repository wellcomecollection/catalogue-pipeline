package weco.catalogue.tei.id_extractor

import scalikejdbc._

import java.time.{Instant, ZoneId, ZonedDateTime}

/** Represents a set of identifiers as stored in MySQL */
case class PathId(
                       path: String,
                       id: String,
                       timeModified: ZonedDateTime
)

object PathId {
  def apply(p: SyntaxProvider[PathId])(rs: WrappedResultSet): PathId = {
    PathId(
      path = rs.string(p.resultName.path),
      id = rs.string(p.resultName.id),
      timeModified = ZonedDateTime.ofInstant(Instant.ofEpochMilli(rs.long(p.resultName.timeModified)), ZoneId.of("Z"))
    )
  }

}
