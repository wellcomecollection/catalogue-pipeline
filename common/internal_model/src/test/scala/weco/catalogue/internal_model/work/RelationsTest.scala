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
      List(Relation("Granny"), Relation("Grandpa")),
      List(Relation("Baby")),
      List(Relation("Big Sister")),
      List(Relation("Little Brother"))
    ).size shouldBe 5
  }

  it(
    "Shows the total number of known relations even when not all relation lists are populated") {
    Relations(
      List(Relation("Granny"), Relation("Grandpa"), Relation("Mum")),
      List(Relation("Baby")),
      Nil,
      List(Relation("Little Brother"), Relation("Little Sister"))
    ).size shouldBe 6
  }
}

class RelationTest extends AnyFunSpec with Matchers with WorkGenerators {

  it("Creates a Relation by extracting relevant properties from a Work") {
    val work = indexedWork()
    val relation = Relation(work, 1, 2, 3)
    relation.id.get shouldBe work.state.canonicalId
    relation.title shouldBe work.data.title
    relation.collectionPath shouldBe work.data.collectionPath
    relation.depth shouldBe 1
    relation.numChildren shouldBe 2
    relation.numDescendents shouldBe 3
  }

  it("Creates a Series Relation from a title") {
    val relation = Relation("hello")
    relation.title.get shouldBe "hello"
    relation.workType shouldBe WorkType.Series

    relation.collectionPath shouldBe None
    relation.id shouldBe None
    relation.depth shouldBe 0
    relation.numChildren shouldBe 0
    relation.numDescendents shouldBe 0
  }

}
