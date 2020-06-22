package uk.ac.wellcome.elasticsearch.typesafe

import com.sksamuel.elastic4s.ElasticClient
import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.ElasticClientBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object ElasticBuilder {
  def buildElasticClient(config: Config): ElasticClient = {
    val hostname = config
      .getStringOption("es.host")
      .getOrElse("localhost")
    val port = config
      .getIntOption("es.port")
      .getOrElse(9200)
    val protocol = config
      .getStringOption("es.protocol")
      .getOrElse("http")
    val username = config
      .getStringOption("es.username")
      .getOrElse("username")
    val password = config
      .getStringOption("es.password")
      .getOrElse("password")

    ElasticClientBuilder.create(
      hostname = hostname,
      port = port,
      protocol = protocol,
      username = username,
      password = password
    )
  }
}
