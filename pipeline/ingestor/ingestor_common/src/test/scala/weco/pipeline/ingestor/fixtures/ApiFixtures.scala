package weco.pipeline.ingestor.fixtures

import io.circe.Json
import io.circe.syntax._
import weco.fixtures.RandomGenerators
import weco.json.JsonUtil._

import java.io.{File, PrintWriter}
import java.time.Instant
import scala.util.Random

trait ApiFixtures extends RandomGenerators {
  case class Fixture(description: String, createdAt: Instant = Instant.now(), id: String, document: Json)

  override protected lazy val random: Random =
    new Random(0)

  def saveFixtures(fixtures: List[(String, Fixture)]): Unit =
    fixtures.foreach { case (id, fixture) =>
      val file = new File(s"fixtures/$id.json")
      val pw = new PrintWriter(file)
      pw.write(fixture.asJson.spaces2)
      pw.close()
    }
}
