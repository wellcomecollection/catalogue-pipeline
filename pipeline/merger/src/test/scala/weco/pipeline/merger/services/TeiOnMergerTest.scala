package weco.pipeline.merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.work.{
  InternalWork,
  MergeCandidate,
  Work,
  WorkData
}

// Tests here will eventually be folded into PlatformMergerTest.scala
// This allows us to test using the TeiOnMerger until we make that the default merger
class TeiOnMergerTest
    extends AnyFunSpec
    with SourceWorkGenerators
    with Matchers {

  it("merges a physical sierra with a tei") {
    val merger = PlatformMerger
    val physicalWork =
      sierraIdentifiedWork()
        .items(List(createIdentifiedPhysicalItem))
    val teiWork = teiIdentifiedWork().mergeCandidates(
      List(
        MergeCandidate(
          id = IdState.Identified(
            canonicalId = physicalWork.state.canonicalId,
            sourceIdentifier = physicalWork.state.sourceIdentifier
          ),
          reason = "Physical/digitised Sierra work"
        )
      )
    )

    val result = merger
      .merge(works = Seq(teiWork, physicalWork))
      .mergedWorksWithTime(now)
    val redirectedWorks = result.collect {
      case w: Work.Redirected[Merged] => w
    }
    val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

    redirectedWorks should have size 1
    visibleWorks should have size 1

    visibleWorks.head.state.canonicalId shouldBe teiWork.state.canonicalId
  }

  it("copies the thumbnail to the inner works") {
    val teiWork = teiIdentifiedWork()
      .mapState {
        _.copy(
          internalWorkStubs = List(
            InternalWork.Identified(
              sourceIdentifier = createTeiSourceIdentifier,
              canonicalId = createCanonicalId,
              workData = WorkData(
                title = Some(s"tei-inner-${randomAlphanumeric(length = 10)}")
              )
            )
          )
        )
      }

    val sierraWork = sierraPhysicalIdentifiedWork()

    val metsWork = metsIdentifiedWork()
      .thumbnail(createDigitalLocation)

    val result =
      PlatformMerger
        .merge(works = Seq(teiWork, sierraWork, metsWork))
        .mergedWorksWithTime(now)

    val visibleWorks = result.collect { case w: Work.Visible[Merged] => w }

    visibleWorks.foreach {
      _.data.thumbnail shouldBe metsWork.data.thumbnail
    }
  }
}
