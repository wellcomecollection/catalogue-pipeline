package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.work.InstantRange

import java.time.{Instant, LocalDateTime}
import org.scalacheck.Arbitrary
import org.scalacheck.Gen.chooseNum
import weco.fixtures.RandomGenerators

import scala.concurrent.duration._

trait InstantGenerators extends RandomGenerators {

  // We use this for the scalacheck of the java.time.Instant type
  // We could just import the library, but I might wait until we need more
  // Taken from here:
  // https://github.com/rallyhealth/scalacheck-ops/blob/master/core/src/main/scala/org/scalacheck/ops/time/ImplicitJavaTimeGenerators.scala
  implicit val arbitraryInstant: Arbitrary[Instant] =
    Arbitrary {
      // Clamp generated dates between year 1 and year 9,999. This is sufficient (we do not expect to see values
      // outside of this range in production data) and avoids errors when indexing test data into Elasticsearch.
      val minEpochSecond = Instant.parse("0001-01-01T00:00:00Z").getEpochSecond
      val maxEpochSecond = Instant.parse("9999-01-01T00:00:00Z").getEpochSecond

      for {
        seconds <- chooseNum(
          minEpochSecond,
          maxEpochSecond
        )
        nanos <- chooseNum(0, 999999999)
      } yield {
        Instant.ofEpochSecond(seconds).plusNanos(nanos)
      }
    }

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
    max - ((random.nextLong() % maxBefore.toSeconds) seconds)

  def instantInLast30Days = randomInstantBefore(now, 30 days)

  implicit class InstantArithmetic(val instant: Instant) {
    def +(duration: FiniteDuration): Instant = doOperation(_ + _, duration)
    def -(duration: FiniteDuration): Instant = doOperation(_ - _, duration)

    def doOperation(
      op: (Long, Long) => Long,
      duration: FiniteDuration
    ): Instant =
      Instant.ofEpochSecond(
        op(instant.getEpochSecond, duration.toSeconds)
      )
  }
}
