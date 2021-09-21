package weco.pipeline.merger.rules

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.CollectionPath
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.pipeline.merger.models.FieldMergeResult

class CollectionPathRuleTest extends AnyFunSpec
  with SourceWorkGenerators with Inside with Matchers {

  val expectedCollectionPath = CollectionPath("A/B/C/1")
  val calmWork = calmIdentifiedWork().collectionPath(expectedCollectionPath)
  val teiWork = teiIdentifiedWork()

  it(
    "puts the calm collectionPath on the tei work") {
    inside(CollectionPathRule.merge(teiWork, List(calmWork))) {
      case FieldMergeResult(collectionPath, _) =>
        collectionPath shouldBe defined
        collectionPath shouldBe Some(expectedCollectionPath)
    }
  }

  it("doesn't copy the collectionPath from a non calm work"){
    inside(CollectionPathRule.merge(teiWork, List(sierraIdentifiedWork().collectionPath(expectedCollectionPath)))) {
      case FieldMergeResult(collectionPath, _) =>
        collectionPath shouldNot be(defined)
    }
  }

  it("doesn't copy the collectionPath to a non tei work"){
    inside(CollectionPathRule.merge(sierraIdentifiedWork(), List(calmWork))) {
      case FieldMergeResult(collectionPath, _) =>
        collectionPath shouldNot be(defined)
    }
  }

}
