package weco.pipeline.id_minter.config.builders

import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scalikejdbc.ConnectionPool
import weco.pipeline.id_minter.config.models.RDSClientConfig

import scala.jdk.CollectionConverters._

class RDSBuilderTest extends AnyFunSpec with Matchers {
  describe("Building the database client connection pools") {
    it("sets up a connection pool for the primary host") {
      RDSBuilder.buildDB(
        RDSClientConfig(
          primaryHost = "realdeal.example.com",
          replicaHost = "doppelganger.example.com",
          port = 999,
          username = "slartibartfast",
          password = "ssh_its_a_secret",
          maxConnections = 5
        )
      )
      val p = ConnectionPool.get(name = 'primary)
      p.url shouldBe "jdbc:mysql://realdeal.example.com:999"
      p.user shouldBe "slartibartfast"
      p.settings.maxSize shouldBe 5
    }

    it("sets up a connection pool for the replica host") {
      RDSBuilder.buildDB(
        RDSClientConfig(
          primaryHost = "primary.example.com",
          replicaHost = "doppelganger.example.com",
          port = 1234,
          username = "slartibartfast",
          password = "ssh_its_a_secret",
          maxConnections = 12
        )
      )
      val p = ConnectionPool.get(name = 'replica)
      p.url shouldBe "jdbc:mysql://doppelganger.example.com:1234"
      p.user shouldBe "slartibartfast"
      p.settings.maxSize shouldBe 12
    }
  }
  describe("Extracting configuration values") {
    val rawConfig = Map(
      "aws.rds.primary_host" -> "numberone",
      "aws.rds.replica_host" -> "numbertwo",
      "aws.rds.username" -> "rincewind",
      "aws.rds.password" -> "opensesame",
      "aws.rds.maxConnections" -> "678"
    )
    val config = RDSClientConfig(
      ConfigFactory.parseMap(rawConfig.asJava)
    )
    it("extracts the primary host") {
      config.primaryHost shouldBe "numberone"
    }
    it("extracts the replica host") {
      config.replicaHost shouldBe "numbertwo"
    }
    it("extracts the username") {
      config.username shouldBe "rincewind"
    }
    it("extracts the password") {
      config.password shouldBe "opensesame"
    }
    it("extracts the maxConnections") {
      config.maxConnections shouldBe 678
    }
    it("defaults to port 3306") {
      config.port shouldBe 3306
    }
    it("extracts the port value if specified") {
      val configWithPort = RDSClientConfig(
        ConfigFactory.parseMap((rawConfig + ("aws.rds.port" -> 1234)).asJava)
      )
      configWithPort.port shouldBe 1234

    }
  }
}
