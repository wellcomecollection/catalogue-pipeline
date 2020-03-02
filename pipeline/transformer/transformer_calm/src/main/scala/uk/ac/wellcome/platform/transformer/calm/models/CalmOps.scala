package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord

trait CalmOps {

  implicit class CalmRecordOps(record: CalmRecord) {

    def get(key: String): Option[String] =
      getList(key).headOption

    def getList(key: String): List[String] =
      record.data
        .get(key)
        .getOrElse(Nil)
        .filter(_.nonEmpty)

    def getJoined(key: String, seperator: String = " "): Option[String] =
      getList(key) match {
        case Nil   => None
        case items => Some(items.mkString(seperator))
      }
  }
}
