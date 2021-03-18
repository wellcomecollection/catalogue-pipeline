package uk.ac.wellcome.models.work.generators

import java.time.{Instant, LocalDateTime}

import weco.catalogue.internal_model.work.InstantRange

import scala.concurrent.duration._
import scala.util.Random

trait InstantGenerators {

  val now: Instant = Instant.now

  def createInstantRangeWith(
    from: String,
    to: String,
    label: String = ""
  ): InstantRange = InstantRange(
    LocalDateTime.parse(from),
    LocalDateTime.parse(to),
    label
  )

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
