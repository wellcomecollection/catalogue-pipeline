package uk.ac.wellcome.platform.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.{FieldMergeResult, MergeResult}
import uk.ac.wellcome.platform.merger.rules.FieldMergeRule

class MergerTest extends AnyFunSpec with Matchers with WorksGenerators {
  val inputWorks =
    (0 to 5).map(_ => createSierraDigitalWork) ++
      (0 to 5).map(_ => createMiroWork) ++
      (0 to 5).map(_ => createUnidentifiedInvisibleMetsWork)
  val mergedTargetItems = (0 to 3).map(_ => createDigitalItem).toList
  val mergedOtherIdentifiers =
    (0 to 3).map(_ => createSierraSystemSourceIdentifier).toList

  object TestItemsRule extends FieldMergeRule {
    type FieldData = List[Item[Unminted]]

    override def merge(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedTargetItems,
        sources = List(sources.tail.head)
      )
  }

  object TestOtherIdentifiersRule extends FieldMergeRule {
    type FieldData = List[SourceIdentifier]

    override def merge(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): FieldMergeResult[FieldData] =
      FieldMergeResult(
        data = mergedOtherIdentifiers,
        sources = sources.tail.tail)
  }

  object TestMerger extends Merger {
    override protected def findTarget(
      works: Seq[TransformedBaseWork]): Option[UnidentifiedWork] =
      works.headOption.map(_.asInstanceOf[UnidentifiedWork])

    override protected def createMergeResult(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): MergeState =
      for {
        items <- TestItemsRule(target, sources)
        otherIdentifiers <- TestOtherIdentifiersRule(target, sources)
      } yield
        MergeResult(
          mergedTarget = target withData { data =>
            data.copy(
              items = items,
              otherIdentifiers = otherIdentifiers
            )
          },
          images = Nil
        )
  }

  val mergedWorks = TestMerger.merge(inputWorks)

  it("returns a single target work as specified") {
    mergedWorks.works should contain(
      inputWorks.head.asInstanceOf[UnidentifiedWork] withData { data =>
        data.copy(
          items = mergedTargetItems,
          otherIdentifiers = mergedOtherIdentifiers
        )
      }
    )
  }

  it(
    "returns redirects for all sources that were marked as such by any field rule") {
    mergedWorks.works.collect {
      case redirect: UnidentifiedRedirectedWork => redirect.sourceIdentifier
    } should contain theSameElementsAs
      inputWorks.tail.tail.map(_.sourceIdentifier)
  }

  it("returns all non-redirected and non-target works untouched") {
    mergedWorks.works should contain(inputWorks.tail.head)
  }
}
