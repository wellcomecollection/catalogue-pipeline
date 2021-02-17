package uk.ac.wellcome.platform.calm_deletion_checker

import akka.http.scaladsl.model.headers.Cookie
import org.scalacheck.{Gen, Shrink}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Milliseconds, Span}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.platform.calm_api_client.{CalmSearchRequest, CalmSession}
import uk.ac.wellcome.platform.calm_api_client.fixtures.CalmApiClientFixtures
import uk.ac.wellcome.platform.calm_deletion_checker.fixtures.CalmSourcePayloadGenerators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefectiveCheckerTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with PatienceConfiguration
    with ScalaCheckPropertyChecks
    with CalmSourcePayloadGenerators
    with CalmApiClientFixtures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(500, Milliseconds)),
    interval = scaled(Span(25, Milliseconds))
  )

  val cookie = Cookie("name", "value")

  describe("DefectiveChecker") {
    implicit val noShrink: Shrink[Int] = Shrink.shrinkAny
    val batches = for {
      n <- Gen.choose(1, 1000)
      d <- Gen.choose(0, n)
    } yield (n, d)

    it("correctly finds defectives in a set of items") {
      forAll(batches) {
        case (n, d) =>
          val items = (1 to n).map(_ => randomAlphanumeric(10))
          val defectiveItems = randomSample(items, size = d)
          val testChecker = new TestDefectiveChecker(defectiveItems)

          whenReady(testChecker.defectiveRecords(items.toSet)) {
            foundDefective =>
              foundDefective shouldBe defectiveItems.toSet
          }
      }
    }

    it("makes fewer queries than the known upper bound") {
      forAll(batches) {
        case (n, d) =>
          val items = (1 to n).map(_ => randomAlphanumeric(10))
          val defectiveItems = randomSample(items, size = d)
          val testChecker = new TestDefectiveChecker(defectiveItems)

          whenReady(testChecker.defectiveRecords(items.toSet)) { _ =>
            testChecker.nTests should be <= testChecker.nTestsUpperBound(n, d)
          }
      }
    }

    it("fails when one of its tests fails") {
      val items = (1 to 100).map(_ => randomAlphanumeric(10))
      val failingChecker = new DefectiveChecker[String] {
        var nTests = 0
        protected def test(items: Set[String]): Future[Int] = {
          nTests += 1
          if (nTests < 50) {
            Future.successful(items.size / 4)
          } else {
            Future.failed(new RuntimeException("oops"))
          }
        }
      }

      whenReady(failingChecker.defectiveRecords(items.toSet).failed) { e =>
        e shouldBe a[RuntimeException]
      }
    }

    class TestDefectiveChecker(deleted: Seq[String])
        extends DefectiveChecker[String] {
      val deletedSet = deleted.toSet
      var nTests = 0
      protected def test(items: Set[String]): Future[Int] = {
        nTests += 1
        Future.successful((items intersect deletedSet).size)
      }
    }
  }

  describe("ApiDeletionChecker") {
    it("performs Calm API searches to count deletions") {
      val nRecords = 10
      withTestCalmApiClient(
        handleSearch = _ => CalmSession(nRecords, cookie)
      ) { apiClient =>
        val records = (1 to nRecords).map(_ => calmSourcePayload)
        val deletionChecker = new ApiDeletionChecker(apiClient)

        whenReady(deletionChecker.defectiveRecords(records.toSet)) { _ =>
          apiClient.requests should have length 1 // Because all records are deleted
          every(apiClient.requests.map(_._1)) shouldBe a[CalmSearchRequest]
        }
      }
    }

    it("fails if the count doesn't make sense") {
      val nRecords = 10
      withTestCalmApiClient(
        handleSearch = _ => CalmSession(nRecords + 1, cookie)
      ) { apiClient =>
        val records = (1 to nRecords).map(_ => calmSourcePayload)
        val deletionChecker = new ApiDeletionChecker(apiClient)

        whenReady(deletionChecker.defectiveRecords(records.toSet).failed) { e =>
          e.getMessage should startWith("More results returned")
        }
      }
    }
  }
}
