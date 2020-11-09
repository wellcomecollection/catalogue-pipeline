package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{
  IdentifierType,
  MergeCandidate,
  SourceIdentifier
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  SierraMaterialType,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import org.scalatest.prop.TableDrivenPropertyChecks._

class SierraMergeCandidatesTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val mergeCandidateBibNumber = "b21414440"
  val miroID = "A0123456"

  describe("physical/digital Sierra work") {
    it("extracts the bib number in 776$$w as a mergeCandidate") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)$mergeCandidateBibNumber")
        )
      )

      SierraMergeCandidates(sierraData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("strips spaces in tag 776$$w and adds it as a mergeCandidate") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)  $mergeCandidateBibNumber")
        )
      )

      SierraMergeCandidates(sierraData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("returns an empty list if MARC tag 776 does not contain a subfield w") {
      val sierraData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "776",
            subfields = List(
              MarcSubfield(tag = "a", content = s"blah blah")
            )
          )
        )
      )

      SierraMergeCandidates(sierraData) shouldBe Nil
    }

    it("ignores values in 776$$w that aren't prefixed with (UkLW)") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List("(OCoLC)14322288")
        )
      )

      SierraMergeCandidates(sierraData) shouldBe Nil
    }

    it(
      "does not create a merge candidate if there are multiple distinct instances of 776$$w") {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)  $mergeCandidateBibNumber", "(UkLW)b12345678")
        )
      )

      SierraMergeCandidates(bibData) shouldBe Nil
    }

    it(
      "creates a merge candidate if there are multiple 776$$w for the same value") {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(
            s"(UkLW)  $mergeCandidateBibNumber",
            s"(UkLW)  $mergeCandidateBibNumber",
            s"(UkLW)$mergeCandidateBibNumber",
          )
        )
      )

      SierraMergeCandidates(bibData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }
  }

  describe("Miro/Sierra work") {
    it("extracts a MIRO ID from a URL in MARC tag 962 subfield u") {
      val bibData = createPictureWithUrls(
        urls = List(s"http://wellcomeimages.org/indexplus/image/$miroID.html")
      )

      SierraMergeCandidates(bibData) shouldBe
        miroMergeCandidate(miroID)
    }

    it(
      "Puts a merge candidate for multiple distinct instances of 962 subfield u") {
      val bibData = createPictureWithUrls(
        urls = List(
          s"http://wellcomeimages.org/indexplus/image/$miroID.html",
          "http://wellcomeimages.org/ixbin/hixclient?MIROPAC=B0000001"
        )
      )

      SierraMergeCandidates(bibData) should contain theSameElementsAs (
        miroMergeCandidate(miroID) ++ miroMergeCandidate("B0000001")
      )
    }

    it("creates a merge candidate if multiple URLs point to the same Miro ID") {
      val bibData = createPictureWithUrls(
        urls = List(
          s"http://wellcomeimages.org/indexplus/image/$miroID.html",
          s"http://wellcomeimages.org/ixbin/hixclient?MIROPAC=$miroID"
        )
      )

      SierraMergeCandidates(bibData) shouldBe
        miroMergeCandidate(miroID)
    }

    it("does not create a merge candidate if the URL is unrecognised") {
      val bibData = createPictureWithUrls(
        urls = List(
          "http://film.wellcome.ac.uk:15151/mediaplayer.html?fug_7340-1&pw=524ph=600.html")
      )

      SierraMergeCandidates(bibData) shouldBe Nil
    }

    it("creates a merge candidate if the material type is 'Picture'") {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType(code = "k")),
        varFields = create962subfieldsWith(
          urls = List(s"http://wellcomeimages.org/indexplus/image/$miroID.html")
        )
      )

      SierraMergeCandidates(bibData) shouldBe miroMergeCandidate(miroID)
    }

    // - - - - - - -  089 fields - - - - - - -
    it(
      "merges a MIRO ID for a picture with MARC tag 089 subfield a if there is no 962 tag") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(bibData) shouldBe miroMergeCandidate(miroID = "V0013889")
    }

    it(
      "merges a MIRO ID for a digital image with MARC tag 089 subfield a if there is no 962 tag") {
      val bibData = createDigitalImageWith(
        varFields = create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(bibData) shouldBe miroMergeCandidate(miroID = "V0013889")
    }

    it("Merges multiple ids in MARC tag 089") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V 13889", "V 12"))
      )

      SierraMergeCandidates(bibData) should contain theSameElementsAs (
        miroMergeCandidate("V0013889") ++ miroMergeCandidate("V0000012")
      )
    }

    it("Merge from tag 962 as well as 089 tag") {
      val bibData = createPictureWith(
        varFields =
          create962subfieldsForWellcomeImageUrl(miroID)
            ++ create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(bibData) should contain theSameElementsAs
        miroMergeCandidate(miroID) ++ miroMergeCandidate("V0013889")
    }

    it(
      "overrides non-suffixed Miro IDs with suffixed ones where those are present") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V0036036")) ++
          create962subfieldsForWellcomeImageUrl("V0036036EL")
      )

      SierraMergeCandidates(bibData) should contain theSameElementsAs
        miroMergeCandidate("V0036036EL")
    }

    it("deduplicates identical Miro IDs across the 089 and 962 fields") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List(miroID)) ++
          create962subfieldsForWellcomeImageUrl(miroID)
      )

      val result = SierraMergeCandidates(bibData)
      result should contain theSameElementsAs miroMergeCandidate(miroID)
    }

    it(
      "retains non-suffixed Miro IDs when there is no matching suffixed ID present") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V0036036")) ++
          create962subfieldsForWellcomeImageUrl("V0012345EBR")
      )

      SierraMergeCandidates(bibData) should contain theSameElementsAs
        miroMergeCandidate("V0036036") ++ miroMergeCandidate("V0012345EBR")
    }
  }

  describe("physical/digital and single-page merges across works") {
    it(
      "creates merge candidates for both physical/digital Sierra works and Miro works") {
      val varFields =
        create776subfieldsWith(ids = List(s"(UkLW)$mergeCandidateBibNumber")) ++
          create962subfieldsForWellcomeImageUrl(miroID)

      val sierraData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("k")),
        varFields = varFields
      )

      val expectedMergeCandidates =
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber) ++
          miroMergeCandidate(miroID)

      SierraMergeCandidates(sierraData) shouldBe expectedMergeCandidates
    }

    it("returns an empty list if there is no MARC tag 776 or 962") {
      val sierraData = createSierraBibDataWith(varFields = List())
      SierraMergeCandidates(sierraData) shouldBe Nil
    }
  }

  describe("Calm/Sierra harvest") {
    it("Assumes UUIDs in 035 a are from calm and attaches the merge candidate") {
      def calmId = randomUUID.toString

      val calmIds = (1 to 5).map(_ => calmId)
      val calmMergeCandidates = calmIds map createCalmMergeCandidate
      val otherIds = (1 to 5).map(_.toString)
      val examples = Table(
        ("-bibData-", "-mergeCandidates-", "-test-"),
        (
          bibDataWith035(calmIds.take(1)),
          calmMergeCandidates.take(1),
          "Single Calm ID"),
        (
          bibDataWith035(calmIds.take(5)),
          calmMergeCandidates.take(5),
          "Multiple Calm IDs"),
        (
          bibDataWith035(calmIds ++ calmIds),
          calmMergeCandidates.take(5),
          "Duped Calm IDs"),
        (
          bibDataWith035(otherIds ++ calmIds),
          calmMergeCandidates.take(5),
          "Mixed Calm and not Calm IDs"),
        (bibDataWith035(otherIds), Nil, "No calm IDs"),
      )

      forAll(examples) { (bibData, mergeCandidates, clue) =>
        withClue(clue) {
          SierraMergeCandidates(bibData) shouldBe mergeCandidates
        }
      }
    }
  }

  private def bibDataWith035(calmIds: Seq[String]) =
    createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("035"),
          subfields = calmIds.map(MarcSubfield("a", _)).toList
        )))

  private def createCalmMergeCandidate(calmId: String) = MergeCandidate(
    identifier = SourceIdentifier(
      identifierType = IdentifierType("calm-record-id"),
      ontologyType = "Work",
      value = calmId
    ),
    reason = "Calm/Sierra harvest"
  )

  private def createPictureWithUrls(urls: List[String]): SierraBibData =
    createPictureWith(varFields = create962subfieldsWith(urls = urls))

  private def createPictureWith(varFields: List[VarField]): SierraBibData =
    createBibDataWith(varFields = varFields, materialTypeCode = 'q')

  private def createDigitalImageWith(varFields: List[VarField]): SierraBibData =
    createBibDataWith(varFields = varFields, materialTypeCode = 'q')

  private def createBibDataWith(varFields: List[VarField],
                                materialTypeCode: Char) = {
    createSierraBibDataWith(
      materialType = Some(SierraMaterialType(code = materialTypeCode.toString)),
      varFields = varFields
    )
  }

  private def create776subfieldsWith(ids: List[String]): List[VarField] =
    ids.map { idString =>
      createVarFieldWith(
        marcTag = "776",
        subfields = List(
          MarcSubfield(tag = "w", content = idString)
        )
      )
    }

  private def create962subfieldsWith(urls: List[String]): List[VarField] =
    urls.map { url =>
      createVarFieldWith(
        marcTag = "962",
        subfields = List(
          MarcSubfield(tag = "u", content = url)
        )
      )
    }

  private def create962subfieldsForWellcomeImageUrl(miroId: String) =
    create962subfieldsWith(
      urls = List(s"http://wellcomeimages.org/indexplus/image/$miroId.html")
    )

  private def create089subfieldsWith(miroIds: List[String]): List[VarField] =
    miroIds.map { miroId =>
      createVarFieldWith(
        marcTag = "089",
        subfields = List(
          MarcSubfield(tag = "a", content = miroId)
        )
      )
    }

  private def physicalAndDigitalSierraMergeCandidate(
    bibNumber: String): List[MergeCandidate] =
    List(
      MergeCandidate(
        identifier = SourceIdentifier(
          identifierType = IdentifierType("sierra-system-number"),
          ontologyType = "Work",
          value = bibNumber
        ),
        reason = "Physical/digitised Sierra work"
      )
    )

  private def miroMergeCandidate(
    miroID: String,
    reason: String = "Miro/Sierra work"): List[MergeCandidate] =
    List(
      MergeCandidate(
        identifier = SourceIdentifier(
          identifierType = IdentifierType("miro-image-number"),
          ontologyType = "Work",
          value = miroID
        ),
        reason = reason
      )
    )
}
