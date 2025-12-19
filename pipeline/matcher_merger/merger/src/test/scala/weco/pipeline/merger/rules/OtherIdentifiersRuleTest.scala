package weco.pipeline.merger.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, Inspectors, LoneElement}
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.work.{Format, Work, WorkState}
import weco.pipeline.matcher.generators.MergeCandidateGenerators
import weco.pipeline.merger.models.FieldMergeResult

class OtherIdentifiersRuleTest
    extends AnyFunSpec
    with Matchers
    with SourceWorkGenerators
    with MergeCandidateGenerators
    with Inside
    with LoneElement
    with Inspectors {
  val nothingWork: Work.Visible[WorkState.Identified] = identifiedWork(
    sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "fake",
      ontologyType = "Work"
    )
  )

  val teiWork: Work.Visible[WorkState.Identified] = teiIdentifiedWork()

  val miroWork: Work.Visible[WorkState.Identified] = miroIdentifiedWork()

  val metsWorks: List[Work.Invisible[WorkState.Identified]] =
    (0 to 3).map {
      _ =>
        metsIdentifiedWork().invisible()
    }.toList

  val physicalSierraWork: Work.Visible[WorkState.Identified] =
    sierraPhysicalIdentifiedWork().format(Format.Pictures)

  val zeroItemPhysicalSierra: Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork()
      .items(List.empty)
      .format(Format.Pictures)

  val physicalMapsSierraWork: Work.Visible[WorkState.Identified] =
    sierraPhysicalIdentifiedWork().format(Format.Maps)

  val sierraWorkWithTwoPhysicalItems: Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork()
      .items((1 to 2).map {
        _ =>
          createIdentifiedPhysicalItem
      }.toList)

  val calmWork: Work.Visible[WorkState.Identified] =
    calmIdentifiedWork().otherIdentifiers(
      List(
        createSourceIdentifierWith(identifierType = IdentifierType.CalmRefNo),
        createSourceIdentifierWith(identifierType = IdentifierType.CalmAltRefNo)
      )
    )

  val mergeCandidate: Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork()

  val sierraWithMergeCandidate: Work.Visible[WorkState.Identified] =
    sierraPhysicalIdentifiedWork()
      .format(Format.Pictures)
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(mergeCandidate))
      )

  val sierraWithDigcode: Work.Visible[WorkState.Identified] =
    sierraDigitalIdentifiedWork().otherIdentifiers(
      List(
        createSierraSystemSourceIdentifier,
        createDigcodeIdentifier("dighole")
      )
    )

  it("merges METS, Miro, Calm and Sierra source IDs into Tei target") {
    inside(
      OtherIdentifiersRule
        .merge(
          teiWork,
          calmWork :: physicalSierraWork :: nothingWork :: miroWork :: metsWorks
        )
    ) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          List(
            physicalSierraWork.sourceIdentifier,
            miroWork.sourceIdentifier
          ) ++ calmWork.data.otherIdentifiers :+ calmWork.sourceIdentifier :+
          physicalSierraWork.data.otherIdentifiers
            .find(_.identifierType.id == IdentifierType.SierraIdentifier.id)
            .get

        mergedSources should contain theSameElementsAs List(
          physicalSierraWork,
          miroWork,
          calmWork
        )
    }
  }

  it("merges METS, Miro, and Sierra source IDs into Calm target") {
    inside(
      OtherIdentifiersRule
        .merge(
          calmWork,
          physicalSierraWork :: nothingWork :: miroWork :: metsWorks
        )
    ) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          List(
            physicalSierraWork.sourceIdentifier,
            miroWork.sourceIdentifier
          ) ++
          metsWorks.map(_.sourceIdentifier) ++ calmWork.data.otherIdentifiers :+
          physicalSierraWork.data.otherIdentifiers
            .find(_.identifierType.id == IdentifierType.SierraIdentifier.id)
            .get

        mergedSources should contain theSameElementsAs (physicalSierraWork :: miroWork :: metsWorks)
    }
  }
  describe("Miro into Sierra") {
    info("There are two ways for a Miro Work to merge into a Sierra Work:")
    info("1. There is a 1:1 relationship between Miro and Sierra")
    info("2. The Sierra work is a digmiro work (has digmiro/digaids digcode)")
    info(
      "This latter case works in conjunction with ImageDataRule, which ignores " +
        "Miro images for digmiro/digaids Sierra works."
    )

    it(
      "merges a Miro source ID into single-item Sierra work with METS and a single miro merge candidates"
    ) {
      inside(
        OtherIdentifiersRule
          .merge(physicalSierraWork, nothingWork :: miroWork :: metsWorks)
      ) {
        case FieldMergeResult(otherIdentifiers, mergedSources) =>
          otherIdentifiers should contain theSameElementsAs
            miroWork.sourceIdentifier :: physicalSierraWork.data.otherIdentifiers

          mergedSources.loneElement shouldBe miroWork
      }
    }

    it(
      "does not merge any Miro source IDs when there is more than 1 Miro work"
    ) {
      val miroWork2: Work.Visible[WorkState.Identified] = miroIdentifiedWork()
      inside(
        OtherIdentifiersRule
          .merge(physicalSierraWork, List(nothingWork, miroWork, miroWork2))
      ) {
        case FieldMergeResult(otherIdentifiers, mergedSources) =>
          otherIdentifiers should contain theSameElementsAs physicalSierraWork.data.otherIdentifiers
          mergedSources shouldBe empty
      }
    }

    it("merges multiple Miro source IDs into a digmiro Sierra work") {
      val miroWork2: Work.Visible[WorkState.Identified] = miroIdentifiedWork()
      val digmiroSierraWork = physicalSierraWork.otherIdentifiers(
        List(createDigcodeIdentifier("digmiro"))
      )
      inside(
        OtherIdentifiersRule
          .merge(digmiroSierraWork, List(nothingWork, miroWork, miroWork2))
      ) {
        case FieldMergeResult(otherIdentifiers, mergedSources) =>
          otherIdentifiers should contain theSameElementsAs
            miroWork.sourceIdentifier ::
              miroWork2.sourceIdentifier ::
              digmiroSierraWork.data.otherIdentifiers
          mergedSources should contain theSameElementsAs Seq(
            miroWork,
            miroWork2
          )
      }
    }

    it("merges Miro source IDs into a Sierra work with zero items") {
      inside(
        OtherIdentifiersRule.merge(zeroItemPhysicalSierra, List(miroWork))
      ) {
        case FieldMergeResult(otherIdentifiers, mergedSources) =>
          otherIdentifiers should contain theSameElementsAs
            miroWork.sourceIdentifier :: zeroItemPhysicalSierra.data.otherIdentifiers

          mergedSources.loneElement shouldBe miroWork
      }
    }
  }
  it("merges a Sierra work into an Ebsco work with no identifiers added") {
    val (sierraWork, ebscoWork) = sierraEbscoIdentifiedWorkPair()

    inside(OtherIdentifiersRule.merge(ebscoWork, List(sierraWork))) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers shouldBe empty
        mergedSources.loneElement shouldBe sierraWork
    }
  }

  it(
    "does not merge any Miro source IDs into Sierra works with format != picture/digital image/3D object"
  ) {
    inside(
      OtherIdentifiersRule
        .merge(physicalMapsSierraWork, List(nothingWork, miroWork))
    ) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs physicalMapsSierraWork.data.otherIdentifiers
        mergedSources shouldBe empty
    }
  }

  it("appends a linked digitised Sierra work sourceIdentifiers") {
    inside(
      OtherIdentifiersRule
        .merge(
          sierraWithMergeCandidate,
          List(nothingWork, miroWork, mergeCandidate)
        )
    ) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        otherIdentifiers should contain theSameElementsAs
          miroWork.sourceIdentifier :: mergeCandidate.identifiers ++
          sierraWithMergeCandidate.data.otherIdentifiers

        mergedSources should contain theSameElementsAs List(
          miroWork,
          mergeCandidate
        )
    }
  }

  it("merges only digcode identifiers from sources' otherIdentifiers") {
    inside(OtherIdentifiersRule.merge(calmWork, Seq(sierraWithDigcode))) {
      case FieldMergeResult(otherIdentifiers, _) =>
        otherIdentifiers should contain only (
          (calmWork.data.otherIdentifiers ++ sierraWithDigcode.data.otherIdentifiers
            .find(_.identifierType.id == "wellcome-digcode")
            .toList :+
            sierraWithDigcode.sourceIdentifier): _*
        )
    }
  }

  it("only merges miro source identifiers") {
    val miroWorkWithOtherSources =
      miroIdentifiedWork(sourceIdentifier = miroWork.sourceIdentifier)
        .otherIdentifiers(
          List(
            SourceIdentifier(
              identifierType = IdentifierType.MiroLibraryReference,
              ontologyType = "Work",
              value = randomAlphanumeric(32)
            )
          )
        )

    inside(
      OtherIdentifiersRule
        .merge(physicalSierraWork, List(miroWorkWithOtherSources))
    ) {
      case FieldMergeResult(otherIdentifiers, mergeCandidates) =>
        otherIdentifiers should contain theSameElementsAs
          (miroWork.sourceIdentifier :: physicalSierraWork.data.otherIdentifiers)

        mergeCandidates.loneElement shouldBe miroWorkWithOtherSources
    }
  }

  it("does not merge any METS IDs") {
    inside(OtherIdentifiersRule.merge(physicalSierraWork, metsWorks)) {
      case FieldMergeResult(otherIdentifiers, mergedSources) =>
        forAll(otherIdentifiers) {
          id =>
            id.identifierType.id should not be "mets"
        }

        mergedSources shouldBe empty
    }
  }
}
