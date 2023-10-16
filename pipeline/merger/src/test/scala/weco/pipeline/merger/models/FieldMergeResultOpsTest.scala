package weco.pipeline.merger.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import weco.catalogue.internal_model.work.generators.CalmWorkGenerators

class FieldMergeResultOpsTest
    extends AnyFunSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with CalmWorkGenerators
    with FieldMergeResultOps {

  describe("redirectSources") {
    it(
      "returns a State containing all the sources that contributed to the result, with redirected set to true"
    ) {
      val works = Seq(calmIdentifiedWork(), calmIdentifiedWork())
      val result =
        FieldMergeResult[String](data = "hello, world", sources = works)
      val redirectSources = result.redirectSources.run(Map.empty)
      redirectSources.value._2 shouldBe "hello, world"
      redirectSources.value._1.keys should contain theSameElementsAs works
      redirectSources.value._1.values should contain theSameElementsAs Seq(
        true,
        true
      )
    }

    it(
      "retains any previous redirection status"
    ) {
      val preExistingWorks =
        Seq(calmIdentifiedWork(), calmIdentifiedWork(), calmIdentifiedWork())
      val newWorks =
        Seq(calmIdentifiedWork(), calmIdentifiedWork(), calmIdentifiedWork())
      val works = preExistingWorks ++ newWorks
      val result =
        FieldMergeResult[String](data = "hello, world", sources = works.tail)
      val redirectSources =
        result.redirectSources.run(
          Map(
            preExistingWorks.head -> false,
            preExistingWorks(1) -> true,
            preExistingWorks(2) -> false
          )
        )
      redirectSources.value._2 shouldBe "hello, world"
      val expectedState = Map(
        preExistingWorks.head -> false,
        preExistingWorks(1) -> true,
        preExistingWorks(2) -> false,
        newWorks.head -> true,
        newWorks(1) -> true,
        newWorks(2) -> true
      )
      redirectSources.value._1 shouldEqual expectedState
    }

  }
}
