package uk.ac.wellcome.platform.snapshot_generator.akka.sink

import akka.stream.scaladsl.Sink
import uk.ac.wellcome.display.models.DisplayWork

import scala.concurrent.Future

object CountingSink {
  def apply(): Sink[DisplayWork, Future[Int]] =
    Sink.fold[Int, DisplayWork](zero = 0)((total, _) => total + 1)
}
