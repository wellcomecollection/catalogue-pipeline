package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.generators.WorksWithImagesGenerators
import uk.ac.wellcome.platform.merger.models.{FieldMergeResult, MergeResult}
import uk.ac.wellcome.platform.merger.rules.FieldMergeRule
import WorkState.{Merged, Source}
import WorkFsm._

class MergerTest
    extends AnyFunSpec
    with Matchers
    with WorksWithImagesGenerators {
  val inputWorks =
    (0 to 5).map(_ => createSierraDigitalWork) ++
      (0 to 5).map(_ => createMiroWork) ++
      (0 to 5).map(_ => createInvisibleMetsSourceWork)
  val mergedTargetItems = (0 to 3).map(_ => createDigitalItem).toList
  val mergedOtherIdentifiers =
    (0 to 3).map(_ => createSierraSystemSourceIdentifier).toList

  object TestItemsRule extends FieldMergeRule {
    type FieldData = List[Item[IdState.Unminted]]

    override def merge(
      target: Work.Visible[Source],
      sources: Seq[Work[Source]]): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedTargetItems,
        sources = List(sources.tail.head)
      )
  }

  object TestOtherIdentifiersRule extends FieldMergeRule {
    type FieldData = List[SourceIdentifier]

    override def merge(
      target: Work.Visible[Source],
      sources: Seq[Work[Source]]): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedOtherIdentifiers,
        sources = sources.tail.tail)
  }

  object TestMerger extends Merger {
    override protected def findTarget(
      works: Seq[Work[Source]]): Option[Work.Visible[Source]] =
      works.headOption.map(_.asInstanceOf[Work.Visible[Source]])

    override protected def createMergeResult(
      target: Work.Visible[Source],
      sources: Seq[Work[Source]]): MergeState =
      for {
        items <- TestItemsRule(target, sources)
        otherIdentifiers <- TestOtherIdentifiersRule(target, sources)
      } yield
        MergeResult(
          mergedTarget = target
            .withData { data =>
              data.copy[DataState.Unidentified](
                items = items,
                otherIdentifiers = otherIdentifiers
              )
            }
            .transition[Merged](1),
          images = Nil
        )
  }

  val mergedWorks = TestMerger.merge(inputWorks)

  it("returns a single target work as specified") {
    mergedWorks.works should contain(
      inputWorks.head
        .asInstanceOf[Work.Visible[Source]]
        .transition[Merged](1)
        .withData { data =>
          data.copy[DataState.Unidentified](
            items = mergedTargetItems,
            otherIdentifiers = mergedOtherIdentifiers
          )
        }
    )
  }

  it(
    "returns redirects for all sources that were marked as such by any field rule") {
    mergedWorks.works.collect {
      case redirect: Work.Redirected[Merged] => redirect.sourceIdentifier
    } should contain theSameElementsAs
      inputWorks.tail.tail.map(_.sourceIdentifier)
  }

  it("returns all non-redirected and non-target works untouched") {
    mergedWorks.works should contain(
      inputWorks.tail.head.transition[Merged](0)
    )
  }
}
