package weco.pipeline.merger.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.SierraWorkGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.generators.{
  CalmWorkGenerators,
  SierraWorkGenerators
}
import weco.catalogue.internal_model.work.{Format, MergeCandidate}
import weco.pipeline.matcher.generators.MergeCandidateGenerators

class SourcesTest
    extends AnyFunSpec
    with Matchers
    with CalmWorkGenerators
    with SierraWorkGenerators
    with MergeCandidateGenerators {
  describe("findFirstLinkedDigitisedSierraWorkFor") {
    it("returns None if there are no source Works") {
      val physicalWork = sierraPhysicalIdentifiedWork()

      Sources.findFirstLinkedDigitisedSierraWorkFor(
        physicalWork,
        sources = Seq.empty) shouldBe None
    }

    it("returns the first source work with a matching merge candidate") {
      val digitisedWork1 = sierraDigitalIdentifiedWork()
      val digitisedWork2 = sierraDigitalIdentifiedWork()

      val physicalWork =
        sierraPhysicalIdentifiedWork()
          .mergeCandidates(
            List(
              createSierraPairMergeCandidateFor(digitisedWork1),
              createSierraPairMergeCandidateFor(digitisedWork2),
            )
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork1, digitisedWork2))

      result shouldBe Some(digitisedWork1)
    }

    it("finds a matching work if the MergeCandidate is on the digitised work") {
      // There are some bib/e-bib pairs where the 776 $w linking field is on the
      // electronic bib, rather than the physical bib.  In this case, we still
      // need to find to digitised work.
      val physicalWork = sierraPhysicalIdentifiedWork()
      val digitisedWork =
        sierraDigitalIdentifiedWork()
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(physicalWork))
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe Some(digitisedWork)
    }

    it("skips a MergeCandidate with the right ID but wrong reason") {
      val digitisedWork = sierraDigitalIdentifiedWork()

      val physicalWork =
        sierraPhysicalIdentifiedWork()
          .mergeCandidates(
            List(
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = digitisedWork.sourceIdentifier,
                  canonicalId = digitisedWork.state.canonicalId),
                reason = "Linked for a mystery reason"
              )
            )
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe None
    }

    it("skips a MergeCandidate with the right reason but the wrong ID") {
      val digitisedWork = sierraDigitalIdentifiedWork()

      val physicalWork =
        sierraPhysicalIdentifiedWork()
          .mergeCandidates(
            List(
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = createSierraIdentifierSourceIdentifier,
                  canonicalId = createCanonicalId),
                reason = "Physical/digitised Sierra work"
              )
            )
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe None
    }

    it("skips a target work which isn't a Sierra work") {
      val digitisedWork = sierraDigitalIdentifiedWork()

      val physicalWork =
        calmIdentifiedWork()
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(digitisedWork))
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe None
    }

    it("skips a target work which isn't a Sierra physical work") {
      val digitisedWork = sierraDigitalIdentifiedWork()

      val physicalWork =
        sierraDigitalIdentifiedWork()
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(digitisedWork))
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe None
    }

    it("skips a target work which is an AV work") {
      val digitisedWork = sierraDigitalIdentifiedWork()

      val physicalWork =
        sierraPhysicalIdentifiedWork()
          .format(Format.Videos)
          .mergeCandidates(
            List(createSierraPairMergeCandidateFor(digitisedWork))
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe None
    }
  }
}
