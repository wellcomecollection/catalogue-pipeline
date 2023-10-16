package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.WorkFsm._
import cats.data.State
import weco.catalogue.internal_model.work.generators.SierraWorkGenerators
import weco.catalogue.internal_model.identifiers.{
  DataState,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.generators.{
  MetsWorkGenerators,
  MiroWorkGenerators,
  SierraWorkGenerators
}
import weco.catalogue.internal_model.work.{Item, Work}
import weco.pipeline.merger.models.{
  FieldMergeResult,
  MergeResult,
  WorkMergingOps
}
import weco.pipeline.merger.rules.FieldMergeRule

class MergerTest
    extends AnyFunSpec
    with Matchers
    with MetsWorkGenerators
    with MiroWorkGenerators
    with SierraWorkGenerators {
  val inputWorks: Seq[Work[Identified]] =
    (0 to 5).map {
      _ =>
        sierraDigitalIdentifiedWork()
    } ++
      (0 to 5).map(_ => miroIdentifiedWork()) ++
      (0 to 5).map(_ => metsIdentifiedWork().invisible())

  val mergedTargetItems = (0 to 3).map(_ => createDigitalItem).toList
  val mergedOtherIdentifiers =
    (0 to 3).map(_ => createSierraSystemSourceIdentifier).toList

  // Copies the items from the second work
  object CopyItemsRule extends FieldMergeRule {
    type FieldData = List[Item[IdState.Minted]]

    override def merge(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]
    ): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedTargetItems,
        sources = List(sources.tail.head)
      )
  }

  // Copies the identifiers from works 3–(last)
  object CopyOtherIdentifiers extends FieldMergeRule {
    type FieldData = List[SourceIdentifier]

    override def merge(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]
    ): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedOtherIdentifiers,
        sources = sources.tail.tail
      )
  }

  // Merges everything into the first Work in a given input.
  object FirstWorkMerger extends Merger with WorkMergingOps {

    override protected def findTarget(
      works: Seq[Work[Identified]]
    ): Option[Work.Visible[Identified]] =
      works.headOption.map(_.asInstanceOf[Work.Visible[Identified]])

    override protected def createMergeResult(
      target: Work.Visible[Identified],
      sources: Seq[Work[Identified]]
    ): State[MergeState, MergeResult] =
      for {
        items <- CopyItemsRule(target, sources).redirectSources
        otherIdentifiers <- CopyOtherIdentifiers(
          target,
          sources
        ).redirectSources
      } yield MergeResult(
        mergedTarget = target
          .mapData {
            data =>
              data.copy[DataState.Identified](
                items = items,
                otherIdentifiers = otherIdentifiers
              )
          },
        imageDataWithSources = Nil
      )
  }

  val mergedWorks = FirstWorkMerger.merge(inputWorks)

  it("returns a single target work as specified") {
    val mergedWork: Work.Visible[Identified] = mergedWorks
      .mergedWorksWithTime(now)
      .filter {
        w =>
          w.sourceIdentifier == inputWorks.head.sourceIdentifier
      }
      .head
      .asInstanceOf[Work.Visible[Identified]]

    mergedWork.data shouldBe
      inputWorks.head.data.copy[DataState.Identified](
        items = mergedTargetItems,
        otherIdentifiers = mergedOtherIdentifiers
      )
  }

  it("sets the redirectSources on the merged work") {
    val mergedWork: Work.Visible[Identified] = mergedWorks
      .mergedWorksWithTime(now)
      .filter {
        w =>
          w.sourceIdentifier == inputWorks.head.sourceIdentifier
      }
      .head
      .asInstanceOf[Work.Visible[Identified]]

    mergedWork.redirectSources should contain theSameElementsAs
      inputWorks.tail.tail.map {
        w =>
          IdState.Identified(w.state.canonicalId, w.sourceIdentifier)
      }
  }

  it(
    "returns redirects for all sources that were marked as such by any field rule"
  ) {
    mergedWorks.mergedWorksWithTime(now).collect {
      case redirect: Work.Redirected[Merged] => redirect.sourceIdentifier
    } should contain theSameElementsAs
      inputWorks.tail.tail.map(_.sourceIdentifier)
  }

  it("returns all non-redirected and non-target works untouched") {
    mergedWorks.mergedWorksWithTime(now) should contain(
      inputWorks.tail.head.transition[Merged](now)
    )
  }

  it("preserves the existing redirectSources on a target work") {
    val existingRedirectSources = (1 to 3).map {
      _ =>
        IdState.Identified(createCanonicalId, createSourceIdentifier)
    }

    val targetWork = identifiedWork()
      .withRedirectSources(existingRedirectSources)

    val sourceWorks = (1 to 5).map {
      _ =>
        identifiedWork()
    }

    val mergerOutput = FirstWorkMerger.merge(targetWork +: sourceWorks)

    val mergedWork: Work.Visible[Merged] = mergerOutput
      .mergedWorksWithTime(now)
      .collect {
        case w: Work.Visible[Merged]
            if w.sourceIdentifier == targetWork.sourceIdentifier =>
          w
      }
      .head

    mergedWork.redirectSources should contain allElementsOf existingRedirectSources
    mergedWork.redirectSources should contain allElementsOf
      sourceWorks.tail.map {
        w =>
          IdState.Identified(w.state.canonicalId, w.sourceIdentifier)
      }
  }
}
