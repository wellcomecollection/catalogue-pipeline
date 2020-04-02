package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult

class CalmRulesTest extends FunSpec with Matchers with WorksGenerators {

  val target = createSierraPhysicalWork
  val calmWork = createUnidentifiedCalmWork(
    data = WorkData(title = Some("123"))
  )
  val secondCalmWork = createUnidentifiedCalmWork(
    data = WorkData(title = Some("456"))
  )
  val otherWork = createUnidentifiedWorkWith(
    title = Some("789")
  )

  val rule = UseCalmWhenExistsRule(_.data.title)

  it("merges from Calm work when it exists") {
    rule.merge(target, List(otherWork, calmWork)) shouldBe
      FieldMergeResult(Some("123"), List(calmWork))
  }

  it("merges from target work when Calm work doesn't exist") {
    rule.merge(target, List(otherWork)) shouldBe
      FieldMergeResult(target.data.title, Nil)
  }

  it("merges from first Calm work when multiple exists") {
    rule.merge(target, List(calmWork, secondCalmWork)) shouldBe
      FieldMergeResult(Some("123"), List(calmWork, secondCalmWork))
  }
}
