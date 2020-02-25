package uk.ac.wellcome.platform.merger.services

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.models.{FieldMergeResult, MergeResult}

class MergerTest extends FunSpec with Matchers with WorksGenerators {
  val inputWorks =
    (0 to 5).map(_ => createSierraDigitalWork) ++
      (0 to 5).map(_ => createMiroWork) ++
      (0 to 5).map(_ => createUnidentifiedInvisibleMetsWork)
  val mergedTargetItems = (0 to 3).map(_ => createDigitalItem).toList
  val mergedOtherIdentifiers =
    (0 to 3).map(_ => createSierraSystemSourceIdentifier).toList

  object TestMerger extends Merger {
    override protected def findTarget(
      works: Seq[TransformedBaseWork]): Option[UnidentifiedWork] =
      works.headOption.map(_.asInstanceOf[UnidentifiedWork])

    override protected def createMergeResult(
      target: UnidentifiedWork,
      sources: Seq[TransformedBaseWork]): RedirectsAccumulator[MergeResult] =
      for {
        items <- accumulateRedirects(
          FieldMergeResult(
            fieldData = mergedTargetItems,
            redirects = List(sources.tail.head)
          )
        )
        otherIdentifiers <- accumulateRedirects(
          FieldMergeResult(
            fieldData = mergedOtherIdentifiers,
            redirects = sources.tail.tail
          )
        )
      } yield
        MergeResult(
          target = target withData { data =>
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
