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

      SierraMergeCandidates(createSierraBibNumber, sierraData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("strips spaces in tag 776$$w and adds it as a mergeCandidate") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)  $mergeCandidateBibNumber")
        )
      )

      SierraMergeCandidates(createSierraBibNumber, sierraData) shouldBe
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

      SierraMergeCandidates(createSierraBibNumber, sierraData) shouldBe Nil
    }

    it("ignores values in 776$$w that aren't prefixed with (UkLW)") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List("(OCoLC)14322288")
        )
      )

      SierraMergeCandidates(createSierraBibNumber, sierraData) shouldBe Nil
    }

    it(
      "does not create a merge candidate if there are multiple distinct instances of 776$$w") {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)  $mergeCandidateBibNumber", "(UkLW)b12345678")
        )
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe Nil
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

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }
  }

  describe("single-page Miro/Sierra work") {
    it("extracts a MIRO ID from a URL in MARC tag 962 subfield u") {
      val bibData = createPictureWithUrls(
        urls = List(s"http://wellcomeimages.org/indexplus/image/$miroID.html")
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(miroID)
    }

    it(
      "does not put a merge candidate for multiple distinct instances of 962 subfield u") {
      val bibData = createPictureWithUrls(
        urls = List(
          s"http://wellcomeimages.org/indexplus/image/$miroID.html",
          "http://wellcomeimages.org/ixbin/hixclient?MIROPAC=B0000001"
        )
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe Nil
    }

    it("creates a merge candidate if multiple URLs point to the same Miro ID") {
      val bibData = createPictureWithUrls(
        urls = List(
          s"http://wellcomeimages.org/indexplus/image/$miroID.html",
          s"http://wellcomeimages.org/ixbin/hixclient?MIROPAC=$miroID"
        )
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(miroID)
    }

    it("does not create a merge candidate if the URL is unrecognised") {
      val bibData = createPictureWithUrls(
        urls = List(
          "http://film.wellcome.ac.uk:15151/mediaplayer.html?fug_7340-1&pw=524ph=600.html")
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe Nil
    }

    it("creates a merge candidate if the material type is 'Picture'") {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType(code = "k")),
        varFields = create962subfieldsWith(
          urls = List(s"http://wellcomeimages.org/indexplus/image/$miroID.html")
        )
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(miroID)
    }

    // - - - - - - -  089 fields - - - - - - -
    it(
      "merges a MIRO ID for a picture with MARC tag 089 subfield a if there is no 962 tag") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(
          miroID = "V0013889",
          reason = "Single page Miro/Sierra work (secondary source)")
    }

    it(
      "merges a MIRO ID for a digital image with MARC tag 089 subfield a if there is no 962 tag") {
      val bibData = createDigitalImageWith(
        varFields = create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(
          miroID = "V0013889",
          reason = "Single page Miro/Sierra work (secondary source)")
    }

    it("does not merge if there are multiple ids in MARC tag 089") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V 13889", "V 12"))
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe Nil
    }

    it("prefers to merge from tag 962 even if there is a 089 tag") {
      val bibData = createPictureWith(
        varFields =
          create962subfieldsForWellcomeImageUrl(miroID)
            ++ create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(miroID)
    }

    it(
      "does not merge if there are multiple tag 962 ids even if there is a 089 tag") {
      val bibData = createPictureWith(
        varFields =
          create962subfieldsForWellcomeImageUrl("A0123456")
            ++ create962subfieldsForWellcomeImageUrl("V1234567")
            ++ create089subfieldsWith(List("V 13889"))
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe Nil
    }

    // - - - - - - -  Material type - - - - - - -
    it("creates a merge candidate if the material type is '3D objects'") {
      val bibData = create3DObjectWith(
        varFields = create962subfieldsForWellcomeImageUrl(miroID)
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(miroID)
    }

    it("creates a merge candidate if the material type is 'Digital Images'") {
      val bibData = createPictureWith(
        varFields = create962subfieldsForWellcomeImageUrl(miroID)
      )

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe
        singleMiroMergeCandidate(miroID)
    }

    it(
      "does not create a merge candidate if the material type is neither 'Picture', 'Digital Image', nor '3DObject'") {
      val bibData = createBibDataWith(
        varFields = create962subfieldsForWellcomeImageUrl(miroID),
        materialTypeCode = 'x')

      SierraMergeCandidates(createSierraBibNumber, bibData) shouldBe Nil
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
          singleMiroMergeCandidate(miroID)

      SierraMergeCandidates(createSierraBibNumber, sierraData) shouldBe
        expectedMergeCandidates
    }

    it("returns an empty list if there is no MARC tag 776 or 962") {
      val sierraData = createSierraBibDataWith(varFields = List())
      SierraMergeCandidates(createSierraBibNumber, sierraData) shouldBe Nil
    }
  }

  describe("Calm/Sierra harvest") {
    it("Creates merge candidates from 035a from Calm/Sierra harvest") {
      val ids = (1 to 5).map(_ => randomAlphanumeric(36).toString)
      val calmMergeCandidates = ids map createCalmMergeCandidate

      val examples = Table(
        ("-bibData-", "-mergeCandidates-", "-test-"),
        (bibDataWith035(ids.take(1)), calmMergeCandidates.take(1), "Single"),
        (bibDataWith035(ids.take(5)), calmMergeCandidates.take(5), "Multiple"),
        (bibDataWith035(ids ++ ids), calmMergeCandidates.take(5), "Dedupes"),
      )

      forAll(examples) { (bibData, mergeCandidates, clue) =>
        withClue(clue) {
          SierraMergeCandidates(createSierraBibNumber, bibData) should be(
            mergeCandidates)
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
    reason = Some("Calm/Sierra harvest")
  )

  private def createPictureWithUrls(urls: List[String]): SierraBibData =
    createPictureWith(varFields = create962subfieldsWith(urls = urls))

  private def createPictureWith(varFields: List[VarField]): SierraBibData =
    createBibDataWith(varFields = varFields, materialTypeCode = 'q')

  private def createDigitalImageWith(varFields: List[VarField]): SierraBibData =
    createBibDataWith(varFields = varFields, materialTypeCode = 'q')

  private def create3DObjectWith(varFields: List[VarField]): SierraBibData =
    createBibDataWith(varFields = varFields, materialTypeCode = 'r')

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
        reason = Some("Physical/digitised Sierra work")
      )
    )

  private def singleMiroMergeCandidate(
    miroID: String,
    reason: String = "Single page Miro/Sierra work"): List[MergeCandidate] =
    List(
      MergeCandidate(
        identifier = SourceIdentifier(
          identifierType = IdentifierType("miro-image-number"),
          ontologyType = "Work",
          value = miroID
        ),
        reason = Some(reason)
      )
    )
}
