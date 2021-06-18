package weco.catalogue.tei.id_extractor.models

import scalikejdbc._

import java.time.Instant

/** Associates a filepath to its id and last modified time */
case class PathId(
  path: String,
  id: String,
  timeModified: Instant
)

object PathId {
  def apply(p: SyntaxProvider[PathId])(rs: WrappedResultSet): PathId = {
    PathId(
      path = rs.string(p.resultName.path),
      id = rs.string(p.resultName.id),
      timeModified = Instant.ofEpochMilli(rs.long(p.resultName.timeModified))
    )
  }

}
