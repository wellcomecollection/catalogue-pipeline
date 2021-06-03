package weco.catalogue.tei.id_extractor

import java.time.ZonedDateTime
import scala.concurrent.Future

class PathIdDao {
  def getByPath(path: String): Future[Option[Row]] = ???

  def save(id: String, path: String, timeModified: ZonedDateTime): Future[Unit] = ???

}

case class Row(path: String, id: String, timeModified: ZonedDateTime)
