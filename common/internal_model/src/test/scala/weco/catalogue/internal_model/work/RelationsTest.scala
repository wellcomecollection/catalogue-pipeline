package weco.catalogue.internal_model.work

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.work.generators.WorkGenerators

class RelationsTest
    extends AnyFunSpec
    with Matchers
    with LoneElement
    with IdentifiersGenerators {
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

  it("replaces unidentified relations when they match by title") {
    val granny = SeriesRelation("Granny")
    val newGranny = granny.copy(id = Some(createCanonicalId))

    val r1 = Relations(
      ancestors = List(granny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    val r2 = Relations(
      ancestors = List(newGranny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    (r1 + r2).ancestors.loneElement shouldBe newGranny
  }

  it("replaces identified relations") {
    val granny = SeriesRelation("Granny").copy(id = Some(createCanonicalId))
    val newGranny = granny.copy(title = Some("Grandma"))

    val r1 = Relations(
      ancestors = List(granny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    val r2 = Relations(
      ancestors = List(newGranny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    (r1 + r2).ancestors.loneElement shouldBe newGranny
  }

  it(
    "replaces all relations other than unidentified ones that do not have a replacement in the new set"
  ) {
    val grandma = SeriesRelation("Grandma").copy(id = Some(createCanonicalId))
    val newGranny = grandma.copy(title = Some("Granny"))
    val mum = SeriesRelation("Mum")
    val newMum = mum.copy(id = Some(createCanonicalId))
    val greatGrandma =
      SeriesRelation("Great Grandma").copy(id = Some(createCanonicalId))
    val eve = SeriesRelation("Mitochondrial Eve")
    val r1 = Relations(
      ancestors = List(mum, grandma, greatGrandma, eve),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    val r2 = Relations(
      ancestors = List(newMum, newGranny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    (r1 + r2).ancestors shouldBe List(
      eve, // Eve is the only unidentified non-matching entry
      newMum, // mum is replaced by newMum, matching on title
      newGranny
    )
  }

  it("replaces matching relations, even when title and id are the same") {
    val granny = SeriesRelation("Granny").copy(
      id = Some(createCanonicalId),
      numDescendents = 1
    )
    val newGranny = granny.copy(numDescendents = 99)

    val r1 = Relations(
      ancestors = List(granny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    val r2 = Relations(
      ancestors = List(newGranny),
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
    (r1 + r2).ancestors.loneElement.numDescendents shouldBe 99
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
