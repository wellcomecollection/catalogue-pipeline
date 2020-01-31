package uk.ac.wellcome.elasticsearch

import java.security.MessageDigest
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticDsl._

case class ElasticConfig(
  index: Index
)

object ElasticConfig {
  def hashed(namespace: String, config: IndexConfig): ElasticConfig = {
    val checksum =
      MessageDigest
        .getInstance("SHA-256")
        .digest(config.create("").request.entity.get.toString.getBytes("UTF-8"))
        .map("%02x".format(_))
        .mkString
        .toLowerCase

    ElasticConfig(Index(s"${namespace}_${checksum}"))
  }
}
