package uk.ac.wellcome.models.work.generators

import java.time.{Instant, LocalDateTime}

import uk.ac.wellcome.models.work.internal._

import scala.concurrent.duration._
import scala.util.Random

trait InstantGenerators {

  val now: Instant = Instant.now

  def createInstantRangeWith(from: String,
                             to: String,
                             inferred: Boolean = false,
                             label: String = "") =
    InstantRange(LocalDateTime.parse(from), LocalDateTime.parse(to), label)
      .withInferred(inferred)

  def randomInstantBefore(max: Instant, maxBefore: FiniteDuration) =
    max - ((Random.nextLong() % maxBefore.toSeconds) seconds)

  def instantInLast30Days = randomInstantBefore(now, 30 days)

  implicit class InstantArithmetic(val instant: Instant) {
    def +(duration: FiniteDuration): Instant = doOperation(_ + _, duration)
    def -(duration: FiniteDuration): Instant = doOperation(_ - _, duration)

    def doOperation(op: (Long, Long) => Long,
                    duration: FiniteDuration): Instant =
      Instant.ofEpochSecond(
        op(instant.getEpochSecond, duration.toSeconds)
      )
  }
}
