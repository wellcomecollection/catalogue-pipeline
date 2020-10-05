package uk.ac.wellcome.platform.snapshot_generator.akkastreams.sink

import akka.stream.scaladsl.Sink

import scala.concurrent.Future

object CountingSink {
  def apply[T](): Sink[T, Future[Int]] =
    Sink.fold[Int, T](zero = 0)((total, _) => total + 1)
}
