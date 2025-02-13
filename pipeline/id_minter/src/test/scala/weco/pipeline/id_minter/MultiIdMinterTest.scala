package weco.pipeline.id_minter

import io.circe.Json
import io.circe.syntax._
import org.scalatest.LoneElement
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.pipeline.id_minter.fixtures.PredefinedMinter
import weco.pipeline_storage.memory.MemoryRetriever

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class MultiIdMinterTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ScalaFutures
    with IntegrationPatience
    with LoneElement
    with PredefinedMinter {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def createIndex(works: List[Work[Source]]): Map[String, Json] =
    works.map(work => (work.id, work.asJson)).toMap

  val works = sourceWorks(3)

  val upstreamIndex = createIndex(works)
  val jsonRetriever =
    new MemoryRetriever(index = mutable.Map(upstreamIndex.toSeq: _*))

  val idMap = Map(
    works.head.sourceIdentifier -> "aaaaaaaa",
    works(1).sourceIdentifier -> "bbbbbbbb",
    works(2).sourceIdentifier -> "cccccccc"
  )

  val allUpstreamIds = idMap.keys.map(_.toString).toSeq

  describe("running with no problems") {
    it("does nothing if there is nothing to do") {
      whenReady(multiMinter(jsonRetriever, idMap).processSourceIds(Nil)) {
        result =>
          result shouldBe empty
      }
    }

    it("mints ids for the documents in a sequence of ids") {
      whenReady(
        multiMinter(jsonRetriever, idMap).processSourceIds(allUpstreamIds)
      ) {
        result =>
          val r = result.toSeq
          r.map(
            _.right.get.sourceIdentifier
          ) should contain theSameElementsAs idMap.keys
      }
    }
  }

  describe(
    "partial failures when the records are not in the upstream database"
  ) {
    whenReady(
      multiMinter(jsonRetriever, idMap).processSourceIds(
        "MISSING" +: allUpstreamIds :+ "Not Here"
      )
    ) {
      result =>
        val (rights, lefts) = result.partition(_.isRight)
        it("returns successful records as Right values") {
          rights.map(
            _.right.get.sourceIdentifier
          ) should contain theSameElementsAs idMap.keys
        }
        it("returns failed identifiers as Left values") {
          lefts should contain theSameElementsAs Seq(
            Left("MISSING"),
            Left("Not Here")
          )
        }
    }
  }

  describe("partial failures when the minter is unable to coin an identifier") {
    // just like the minter above, but with the second identifier missing
    val idMap = Map(
      works.head.sourceIdentifier -> "aaaaaaaa",
      works(2).sourceIdentifier -> "cccccccc"
    )

    whenReady(
      multiMinter(jsonRetriever, idMap).processSourceIds(allUpstreamIds)
    ) {
      result =>
        val (rights, lefts) = result.partition(_.isRight)
        it("returns successful records as Right values") {
          rights.map(
            _.right.get.sourceIdentifier
          ) should contain theSameElementsAs idMap.keys
        }
        it("returns failed identifiers as Left values") {
          lefts.loneElement shouldBe Left(works(1).sourceIdentifier.toString)
        }
    }
  }

  describe(
    "partial failures for both reasons"
  ) {
    val idMap = Map(
      works.head.sourceIdentifier -> "aaaaaaaa",
      works(2).sourceIdentifier -> "cccccccc"
    )
    whenReady(
      multiMinter(jsonRetriever, idMap).processSourceIds(
        "MISSING" +: allUpstreamIds :+ "Not Here"
      )
    ) {
      result =>
        val (rights, lefts) = result.partition(_.isRight)
        it("returns successful records as Right values") {
          rights.map(
            _.right.get.sourceIdentifier
          ) should contain theSameElementsAs idMap.keys
        }
        it(
          "returns failed identifiers as Left values, regardless of why they failed"
        ) {
          lefts should contain theSameElementsAs Seq(
            Left("MISSING"),
            Left("Not Here"),
            Left(works(1).sourceIdentifier.toString)
          )
        }
    }
  }
}
