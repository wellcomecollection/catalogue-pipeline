package weco.pipeline.transformer.calm.models

import weco.catalogue.source_model.calm.CalmRecord

trait CalmRecordOps {

  implicit class CalmRecordOps(record: CalmRecord) {

    def get(key: String): Option[String] =
      getList(key).distinct.headOption

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
