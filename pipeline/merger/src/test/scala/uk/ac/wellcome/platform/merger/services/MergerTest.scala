package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.{FieldMergeResult, MergeResult}
import uk.ac.wellcome.platform.merger.rules.FieldMergeRule
import WorkState.{Identified, Merged}
import WorkFsm._
import cats.data.State
import uk.ac.wellcome.models.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators,
  SierraWorkGenerators
}

class MergerTest
    extends AnyFunSpec
    with Matchers
    with MetsWorkGenerators
    with MiroWorkGenerators
    with SierraWorkGenerators {
  import Merger.WorkMergingOps

  val inputWorks: Seq[Work[Identified]] =
    (0 to 5).map { _ =>
      sierraDigitalIdentifiedWork()
    } ++
      (0 to 5).map(_ => miroIdentifiedWork()) ++
      (0 to 5).map(_ => metsIdentifiedWork().invisible())

  val mergedTargetItems = (0 to 3).map(_ => createDigitalItem).toList
  val mergedOtherIdentifiers =
    (0 to 3).map(_ => createSierraSystemSourceIdentifier).toList

  object TestItemsRule extends FieldMergeRule {
    type FieldData = List[Item[IdState.Minted]]

    override def merge(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedTargetItems,
        sources = List(sources.tail.head)
      )
  }

  object TestOtherIdentifiersRule extends FieldMergeRule {
    type FieldData = List[SourceIdentifier]

    override def merge(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedOtherIdentifiers,
        sources = sources.tail.tail)
  }

  object TestMerger extends Merger {
    import Merger.WorkMergingOps

    override protected def findTarget(
      works: Seq[Work[Identified]]): Option[Work.Visible[Identified]] =
      works.headOption.map(_.asInstanceOf[Work.Visible[Identified]])

    override protected def createMergeResult(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]): State[MergeState, MergeResult] =
      for {
        items <- TestItemsRule(target, sources).redirectSources
        otherIdentifiers <- TestOtherIdentifiersRule(target, sources).redirectSources
      } yield
        MergeResult(
          mergedTarget = target
            .mapData { data =>
              data.copy[DataState.Identified](
                items = items,
                otherIdentifiers = otherIdentifiers
              )
            },
          imageDataWithSources = Nil
        )
  }

  val mergedWorks = TestMerger.merge(inputWorks)

  it("returns a single target work as specified") {
    mergedWorks.mergedWorksWithTime(now) should contain(
      inputWorks.head
        .asInstanceOf[Work.Visible[Identified]]
        .transition[Merged](Some(now))
        .mapData { data =>
          data.copy[DataState.Identified](
            items = mergedTargetItems,
            otherIdentifiers = mergedOtherIdentifiers
          )
        }
    )
  }

  it(
    "returns redirects for all sources that were marked as such by any field rule") {
    mergedWorks.mergedWorksWithTime(now).collect {
      case redirect: Work.Redirected[Merged] => redirect.sourceIdentifier
    } should contain theSameElementsAs
      inputWorks.tail.tail.map(_.sourceIdentifier)
  }

  it("returns all non-redirected and non-target works untouched") {
    mergedWorks.mergedWorksWithTime(now) should contain(
      inputWorks.tail.head.transition[Merged](Some(now))
    )
  }
}
