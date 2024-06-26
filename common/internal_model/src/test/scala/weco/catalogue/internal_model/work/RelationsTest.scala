package weco.catalogue.internal_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.WorkGenerators

class RelationsTest extends AnyFunSpec with Matchers {
  it("has zero size when empty") {
    Relations.none.size shouldBe 0
  }

  it("Shows the total number of known relations when populated") {
    Relations(
      ancestors = List(SeriesRelation("Granny"), SeriesRelation("Grandpa")),
      children = List(SeriesRelation("Baby")),
      siblingsPreceding = List(SeriesRelation("Big Sister")),
      siblingsSucceeding = List(SeriesRelation("Little Brother"))
    ).size shouldBe 5
  }

  it(
    "Shows the total number of known relations even when not all relation lists are populated"
  ) {
    Relations(
      ancestors = List(
        SeriesRelation("Granny"),
        SeriesRelation("Grandpa"),
        SeriesRelation("Mum")
      ),
      children = List(SeriesRelation("Baby")),
      siblingsPreceding = Nil,
      siblingsSucceeding =
        List(SeriesRelation("Little Brother"), SeriesRelation("Little Sister"))
    ).size shouldBe 6
  }

  it("concatenates each relation list when added together") {
    val r1 = Relations(
      ancestors = List(SeriesRelation("Granny")),
      children = List(SeriesRelation("Daughter")),
      siblingsPreceding = List(SeriesRelation("Big Sister")),
      siblingsSucceeding = List(SeriesRelation("Little Sister"))
    )
    val r2 = Relations(
      ancestors = List(SeriesRelation("Grandpa")),
      children = List(SeriesRelation("Son")),
      siblingsPreceding = List(SeriesRelation("Big Brother")),
      siblingsSucceeding = List(SeriesRelation("Little Brother"))
    )
    r1 + r2 shouldBe Relations(
      ancestors = List(SeriesRelation("Granny"), SeriesRelation("Grandpa")),
      children = List(SeriesRelation("Daughter"), SeriesRelation("Son")),
      siblingsPreceding =
        List(SeriesRelation("Big Sister"), SeriesRelation("Big Brother")),
      siblingsSucceeding =
        List(SeriesRelation("Little Sister"), SeriesRelation("Little Brother"))
    )
  }
}

class RelationTest extends AnyFunSpec with Matchers with WorkGenerators {

  it("Creates a Relation by extracting relevant properties from a Work") {
    val work = denormalisedWork()
    val relation =
      Relation(work = work, depth = 1, numChildren = 2, numDescendents = 3)
    relation.id.get shouldBe work.state.canonicalId
    relation.title shouldBe work.data.title
    relation.collectionPath shouldBe work.data.collectionPath
    relation.depth shouldBe 1
    relation.numChildren shouldBe 2
    relation.numDescendents shouldBe 3
  }

  it("Creates a Series Relation from a title") {
    val relation = SeriesRelation("hello")
    relation.title.get shouldBe "hello"
    relation.workType shouldBe WorkType.Series

    relation.collectionPath shouldBe None
    relation.id shouldBe None
    relation.depth shouldBe 0
    relation.numChildren shouldBe 0
    relation.numDescendents shouldBe 0
  }

}
