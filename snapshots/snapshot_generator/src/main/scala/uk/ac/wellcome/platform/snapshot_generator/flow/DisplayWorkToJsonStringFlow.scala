package uk.ac.wellcome.platform.snapshot_generator.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import grizzled.slf4j.Logging
import uk.ac.wellcome.display.models.{DisplayWork, DisplayWork}
import uk.ac.wellcome.display.json.DisplayJsonUtil
import uk.ac.wellcome.display.models.Implicits._

object DisplayWorkToJsonStringFlow extends Logging {

  def flow: Flow[DisplayWork, String, NotUsed] =
    Flow[DisplayWork]
      .map { obj =>
        obj match {
          case work: DisplayWork => DisplayJsonUtil.toJson(work)
          case _ =>
            throw new IllegalArgumentException(
              s"Unrecognised object: ${obj.getClass}")
        }
      }
}
