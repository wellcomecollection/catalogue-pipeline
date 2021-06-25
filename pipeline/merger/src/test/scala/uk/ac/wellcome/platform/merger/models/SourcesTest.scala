package uk.ac.wellcome.platform.merger.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.SierraWorkGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.generators.{
  CalmWorkGenerators,
  SierraWorkGenerators
}
import weco.catalogue.internal_model.work.{Format, MergeCandidate}

class SourcesTest
    extends AnyFunSpec
    with Matchers
    with CalmWorkGenerators
    with SierraWorkGenerators {
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
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = digitisedWork1.sourceIdentifier,
                  canonicalId = digitisedWork1.state.canonicalId),
                reason = Some("Physical/digitised Sierra work")
              ),
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = digitisedWork2.sourceIdentifier,
                  canonicalId = digitisedWork2.state.canonicalId),
                reason = Some("Physical/digitised Sierra work")
              )
            )
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork1, digitisedWork2))

      result shouldBe Some(digitisedWork1)
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
                reason = Some("Linked for a mystery reason")
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
                reason = Some("Physical/digitised Sierra work")
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
            List(
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = digitisedWork.sourceIdentifier,
                  canonicalId = digitisedWork.state.canonicalId),
                reason = Some("Physical/digitised Sierra work")
              )
            )
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
            List(
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = digitisedWork.sourceIdentifier,
                  canonicalId = digitisedWork.state.canonicalId),
                reason = Some("Physical/digitised Sierra work")
              )
            )
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
            List(
              MergeCandidate(
                id = IdState.Identified(
                  sourceIdentifier = digitisedWork.sourceIdentifier,
                  canonicalId = digitisedWork.state.canonicalId),
                reason = Some("Physical/digitised Sierra work")
              )
            )
          )

      val result =
        Sources.findFirstLinkedDigitisedSierraWorkFor(
          physicalWork,
          sources = Seq(digitisedWork))

      result shouldBe None
    }
  }
}
