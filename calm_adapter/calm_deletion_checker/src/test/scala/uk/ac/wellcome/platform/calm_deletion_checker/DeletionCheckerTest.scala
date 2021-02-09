package uk.ac.wellcome.platform.calm_deletion_checker

import akka.http.scaladsl.model.headers.RawHeader
import org.scalacheck.{Gen, Shrink}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.platform.calm_api_client.fixtures.{
  CalmApiTestClient,
  CalmResponseGenerators
}
import weco.catalogue.source_model.CalmSourcePayload

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class DeletionCheckerTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ScalaCheckPropertyChecks
    with CalmSourcePayloadGenerators
    with CalmApiTestClient
    with CalmResponseGenerators {

  describe("DeletionChecker") {
    implicit val noShrink: Shrink[Int] = Shrink.shrinkAny
    val batches = for {
      n <- Gen.choose(1, 1000)
      d <- Gen.choose(0, n)
    } yield (n, d)

    it("correctly finds deletions in a set of records") {
      forAll(batches) {
        case (n, d) =>
          val records = (1 to n).map(_ => calmSourcePayload)
          val deletedRecords = randomSample(records, size = d)
          val testChecker = new TestDeletionChecker(deletedRecords)

          whenReady(testChecker.deletedRecords(records.toSet)) { foundDeleted =>
            foundDeleted shouldBe deletedRecords.toSet
          }
      }
    }

    it("makes fewer queries than the known upper bound") {
      forAll(batches) {
        case (n, d) =>
          val records = (1 to n).map(_ => calmSourcePayload)
          val deletedRecords = randomSample(records, size = d)
          val testChecker = new TestDeletionChecker(deletedRecords)

          whenReady(testChecker.deletedRecords(records.toSet)) { _ =>
            testChecker.nTests should be <= testChecker.nTestsUpperBound(n, d)
          }
      }
    }

    it("fails when one of its tests fails") {
      val records = (1 to 100).map(_ => calmSourcePayload)
      val failingChecker = new DeletionChecker {
        var nTests = 0
        protected def nDeleted(records: Records): Future[Int] = {
          nTests += 1
          if (nTests < 50) {
            Future.successful(Random.nextInt(10))
          } else {
            Future.failed(new RuntimeException("oops"))
          }
        }
      }

      whenReady(failingChecker.deletedRecords(records.toSet).failed) { e =>
        e shouldBe a[RuntimeException]
      }
    }

    class TestDeletionChecker(deleted: Seq[CalmSourcePayload])
        extends DeletionChecker {
      val deletedSet = deleted.toSet
      var nTests = 0
      protected def nDeleted(records: this.Records): Future[Int] = {
        nTests += 1
        Future.successful(records.count(deletedSet.contains))
      }
    }

    def randomSample[T](seq: Seq[T], size: Int): Seq[T] =
      Random.shuffle(seq).take(size)
  }

  describe("ApiDeletionChecker") {
    it("performs Calm API searches to count deletions") {
      val nRecords = 10
      val responses = List(searchResponse(n = nRecords))
      withCalmClients(responses) {
        case (apiClient, httpClient) =>
          val records = (1 to nRecords).map(_ => calmSourcePayload)
          val deletionChecker = new ApiDeletionChecker(apiClient)

          whenReady(deletionChecker.deletedRecords(records.toSet)) { _ =>
            httpClient.requests should have length 1 // Because all records are deleted
            val soapAction = httpClient.requests.head.headers.collectFirst {
              case RawHeader("SOAPAction", value) => value
            }
            soapAction shouldBe Some("http://ds.co.uk/cs/webservices/Search")
          }
      }
    }

    it("fails if the count doesn't make sense") {
      val nRecords = 10
      val responses = List(searchResponse(n = nRecords + 1))
      withCalmClients(responses) {
        case (apiClient, _) =>
          val records = (1 to nRecords).map(_ => calmSourcePayload)
          val deletionChecker = new ApiDeletionChecker(apiClient)

          whenReady(deletionChecker.deletedRecords(records.toSet).failed) { e =>
            e.getMessage should startWith("More results returned")
          }
      }
    }
  }
}
