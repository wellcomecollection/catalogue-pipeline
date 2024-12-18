package weco.pipeline.relation_embedder.lib

import com.sksamuel.elastic4s.ElasticClient
import com.typesafe.config.Config
import weco.elasticsearch.ElasticClientBuilder

sealed trait ElasticConfig {
  val host: String
  val port: Int
  val protocol: String
}

case class ElasticConfigUsernamePassword(
                                          host: String,
                                          port: Int,
                                          protocol: String,
                                          username: String,
                                          password:String,
                                        ) extends ElasticConfig

case class ElasticConfigApiKey(
                                host: String,
                                port: Int,
                                protocol: String,
                                apiKey: String,
                              ) extends ElasticConfig

// This should be moved up to scala-libs, it's copied from there!

object ElasticBuilder {
  import weco.typesafe.config.builders.EnrichConfig._

  def buildElasticClientConfig(config: Config,
                               namespace: String = ""): ElasticConfig = {
    val hostname = config.requireString(s"es.$namespace.host")
    val port = config
      .getIntOption(s"es.$namespace.port")
      .getOrElse(9200)
    val protocol = config
      .getStringOption(s"es.$namespace.protocol")
      .getOrElse("http")

    (
      config.getStringOption(s"es.$namespace.username"),
      config.getStringOption(s"es.$namespace.password"),
      config.getStringOption(s"es.$namespace.apikey")
    ) match {
      case (Some(username), Some(password), None) =>
        ElasticConfigUsernamePassword(hostname, port, protocol, username, password)
      // Use an API key if specified, even if username/password are also present
      case (_, _, Some(apiKey)) =>
        ElasticConfigApiKey(hostname, port, protocol, apiKey)
      case _ =>
        throw new Throwable(
          s"You must specify username and password, or apikey, in the 'es.$namespace' config")
    }
  }

  def buildElasticClient(config: ElasticConfig): ElasticClient =
    config match {
      case ElasticConfigUsernamePassword(hostname, port, protocol, username, password) =>
        ElasticClientBuilder.create(
          hostname,
          port,
          protocol,
          username,
          password
        )
      case ElasticConfigApiKey(hostname, port, protocol, apiKey) =>
        ElasticClientBuilder.create(hostname, port, protocol, apiKey)
    }


  def buildElasticClient(config: Config,
                         namespace: String = ""): ElasticClient =
    buildElasticClient(buildElasticClientConfig(config, namespace))
}
