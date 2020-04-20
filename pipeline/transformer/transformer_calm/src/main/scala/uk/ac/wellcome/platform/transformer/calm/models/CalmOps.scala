package uk.ac.wellcome.platform.transformer.calm

trait CalmOps {

  implicit class CalmRecordOps(record: CalmRecord) {

    def get(key: String): Option[String] =
      getList(key).headOption

    def getList(key: String): List[String] =
      record.data
        .getOrElse(key, Nil)
        .map(_.trim)
        .filter(_.nonEmpty)

    def getJoined(key: String, separator: String = " "): Option[String] =
      getList(key) match {
        case Nil   => None
        case items => Some(items.mkString(separator))
      }
  }
}
