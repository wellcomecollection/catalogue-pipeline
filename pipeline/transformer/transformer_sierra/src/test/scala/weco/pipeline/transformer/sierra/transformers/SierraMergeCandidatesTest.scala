package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.MergeCandidate
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.{Subfield, VarField}

class SierraMergeCandidatesTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  val mergeCandidateBibNumber = "b21414440"
  val miroID = "A0123456"

  def getMergeCandidates(
    bibData: SierraBibData
  ): List[MergeCandidate[IdState.Identifiable]] =
    SierraMergeCandidates(createSierraBibNumber, bibData)

  describe("physical/digital Sierra work") {
    it("extracts the bib number in 776$$w as a mergeCandidate") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)$mergeCandidateBibNumber")
        )
      )

      getMergeCandidates(sierraData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("trims spaces at the end of the bnumber of the mergeCandidate") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)$mergeCandidateBibNumber ")
        )
      )

      getMergeCandidates(sierraData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("strips spaces in tag 776$$w and adds it as a mergeCandidate") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)  $mergeCandidateBibNumber")
        )
      )

      getMergeCandidates(sierraData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("returns an empty list if MARC tag 776 does not contain a subfield w") {
      val sierraData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "776",
            subfields = List(
              Subfield(tag = "a", content = s"blah blah")
            )
          )
        )
      )

      getMergeCandidates(sierraData) shouldBe Nil
    }

    it("checks for the (UkLW) prefix case-insensitively") {
      val casingVariations = List("UkLW", "uklw", "UkLw", "UKLW")
      val sierraData = casingVariations.map {
        uklw =>
          createSierraBibDataWith(
            varFields = create776subfieldsWith(
              ids = List(s"($uklw)$mergeCandidateBibNumber")
            )
          )
      }
      val mergeCandidates = sierraData.map(getMergeCandidates)

      every(mergeCandidates) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("ignores values in 776$$w that aren't prefixed with (UkLW)") {
      val sierraData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List("(OCoLC)14322288")
        )
      )

      getMergeCandidates(sierraData) shouldBe Nil
    }

    it(
      "does not create a merge candidate if there are multiple distinct instances of 776$$w"
    ) {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(s"(UkLW)  $mergeCandidateBibNumber", "(UkLW)b12345678")
        )
      )

      getMergeCandidates(bibData) shouldBe Nil
    }

    it(
      "creates a merge candidate if there are multiple 776$$w for the same value"
    ) {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(
            s"(UkLW)  $mergeCandidateBibNumber",
            s"(UkLW)  $mergeCandidateBibNumber",
            s"(UkLW)$mergeCandidateBibNumber"
          )
        )
      )

      getMergeCandidates(bibData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("ignores non-UkLW prefixed values") {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(
            s"(OCLC)123456789",
            s"(UkLW)$mergeCandidateBibNumber"
          )
        )
      )

      getMergeCandidates(bibData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }

    it("ignores values that aren't b numbers") {
      val bibData = createSierraBibDataWith(
        varFields = create776subfieldsWith(
          ids = List(
            s"(UkLW)bxxxxxxxx",
            s"(UkLW)$mergeCandidateBibNumber"
          )
        )
      )

      getMergeCandidates(bibData) shouldBe
        physicalAndDigitalSierraMergeCandidate(mergeCandidateBibNumber)
    }
  }

  describe("Miro/Sierra work") {
    it("extracts a MIRO ID from a URL in MARC tag 962 subfield u") {
      val bibData = createPictureWithUrls(
        urls = List(s"http://wellcomeimages.org/indexplus/image/$miroID.html")
      )

      getMergeCandidates(bibData) shouldBe miroMergeCandidate(miroID)
    }

    it(
      "Puts a merge candidate for multiple distinct instances of 962 subfield u"
    ) {
      val bibData = createPictureWithUrls(
        urls = List(
          s"http://wellcomeimages.org/indexplus/image/$miroID.html",
          "http://wellcomeimages.org/ixbin/hixclient?MIROPAC=B0000001"
        )
      )

      getMergeCandidates(bibData) should contain theSameElementsAs (
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

      getMergeCandidates(bibData) shouldBe miroMergeCandidate(miroID)
    }

    it("does not create a merge candidate if the URL is unrecognised") {
      val bibData = createPictureWithUrls(
        urls = List(
          "http://film.wellcome.ac.uk:15151/mediaplayer.html?fug_7340-1&pw=524ph=600.html"
        )
      )

      getMergeCandidates(bibData) shouldBe Nil
    }

    it("creates a merge candidate if the material type is 'Picture'") {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType(code = "k")),
        varFields = create962subfieldsWith(
          urls = List(s"http://wellcomeimages.org/indexplus/image/$miroID.html")
        )
      )

      getMergeCandidates(bibData) shouldBe miroMergeCandidate(miroID)
    }

    // - - - - - - -  089 fields - - - - - - -
    it(
      "merges a MIRO ID for a picture with MARC tag 089 subfield a if there is no 962 tag"
    ) {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V 13889"))
      )

      getMergeCandidates(bibData) shouldBe miroMergeCandidate(
        miroID = "V0013889"
      )
    }

    it(
      "merges a MIRO ID for a digital image with MARC tag 089 subfield a if there is no 962 tag"
    ) {
      val bibData = createDigitalImageWith(
        varFields = create089subfieldsWith(List("V 13889"))
      )

      getMergeCandidates(bibData) shouldBe miroMergeCandidate(
        miroID = "V0013889"
      )
    }

    it("Merges multiple ids in MARC tag 089") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V 13889", "V 12"))
      )

      getMergeCandidates(bibData) should contain theSameElementsAs (
        miroMergeCandidate("V0013889") ++ miroMergeCandidate("V0000012")
      )
    }

    it("Merge from tag 962 as well as 089 tag") {
      val bibData = createPictureWith(
        varFields = create962subfieldsForWellcomeImageUrl(miroID)
          ++ create089subfieldsWith(List("V 13889"))
      )

      getMergeCandidates(bibData) should contain theSameElementsAs
        miroMergeCandidate(miroID) ++ miroMergeCandidate("V0013889")
    }

    it(
      "overrides non-suffixed Miro IDs with suffixed ones where those are present"
    ) {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V0036036")) ++
          create962subfieldsForWellcomeImageUrl("V0036036EL")
      )

      getMergeCandidates(bibData) should contain theSameElementsAs
        miroMergeCandidate("V0036036EL")
    }

    it("deduplicates identical Miro IDs across the 089 and 962 fields") {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List(miroID)) ++
          create962subfieldsForWellcomeImageUrl(miroID)
      )

      getMergeCandidates(
        bibData
      ) should contain theSameElementsAs miroMergeCandidate(miroID)
    }

    it(
      "retains non-suffixed Miro IDs when there is no matching suffixed ID present"
    ) {
      val bibData = createPictureWith(
        varFields = create089subfieldsWith(List("V0036036")) ++
          create962subfieldsForWellcomeImageUrl("V0012345EBR")
      )

      getMergeCandidates(bibData) should contain theSameElementsAs
        miroMergeCandidate("V0036036") ++ miroMergeCandidate("V0012345EBR")
    }
  }

  describe("EBSCO/Sierra e-resource") {
    it("creates an EBSCO merge candidate from the 001 control number field") {
      val bibData = bibDataWith001("ebs12345e")

      getMergeCandidates(bibData) should contain theSameElementsAs List(
        MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.EbscoAltLookup,
            ontologyType = "Work",
            value = "ebs12345e"
          ),
          reason = "EBSCO/Sierra e-resource"
        )
      )
    }
  }

  describe("physical/digital and single-page merges across works") {
    it(
      "creates merge candidates for both physical/digital Sierra works and Miro works"
    ) {
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

      getMergeCandidates(sierraData) shouldBe expectedMergeCandidates
    }

    it("returns an empty list if there is no MARC tag 776 or 962") {
      val sierraData = createSierraBibDataWith(varFields = List())
      getMergeCandidates(sierraData) shouldBe Nil
    }
  }

  describe("Calm/Sierra harvest") {

    it("adds a single Calm ID as a mergeCandidate") {
      val calmId = randomUUID.toString
      val bibData = bibDataWith035(List(calmId))

      getMergeCandidates(bibData) shouldBe List(
        createCalmMergeCandidate(calmId)
      )
    }
    it("adds multiple Calm IDs as mergeCandidates") {
      val calmIds = (1 to 5).map(_ => randomUUID.toString)
      val bibData = bibDataWith035(calmIds)

      getMergeCandidates(bibData) shouldBe calmIds.map(createCalmMergeCandidate)
    }
    it("dedupes Calm IDs and adds as mergeCandidates") {
      val calmIds = (1 to 5).map(_ => randomUUID.toString)

      val bibData = bibDataWith035(calmIds ++ calmIds)

      getMergeCandidates(bibData) shouldBe calmIds.map(createCalmMergeCandidate)
    }
    it(
      "creates calm merge candidates if it has a mix of calm and non calm identifiers"
    ) {
      val calmIds = (1 to 5).map(_ => randomUUID.toString)
      val otherIds = (1 to 5).map(_.toString)
      val bibData = bibDataWith035(otherIds ++ calmIds)

      getMergeCandidates(bibData) shouldBe calmIds.map(createCalmMergeCandidate)
    }
    it("doesn't create merge candidates if there are no calm ids") {
      val otherIds = (1 to 5).map(_.toString)
      val bibData = bibDataWith035(otherIds)

      getMergeCandidates(bibData) shouldBe Nil
    }

    it("ignores a malformed OCoLC value in 035 subfield ǂa") {
      // This is based on a value from b11955491
      val bibData = bibDataWith035(Seq("(OCoLC)92747927 "))

      getMergeCandidates(bibData) shouldBe empty
    }
  }

  private def bibDataWith001(controlNumber: String) =
    createSierraBibDataWith(
      varFields = List(
        VarField(
          content = Some(controlNumber),
          marcTag = Some("001"),
          fieldTag = Some("o")
        )
      )
    )

  private def bibDataWith035(calmIds: Seq[String]) =
    createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "035",
          subfields = calmIds.map(Subfield("a", _)).toList
        )
      )
    )

  private def createCalmMergeCandidate(calmId: String) = MergeCandidate(
    identifier = SourceIdentifier(
      identifierType = IdentifierType.CalmRecordIdentifier,
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

  private def createBibDataWith(
    varFields: List[VarField],
    materialTypeCode: Char
  ) = {
    createSierraBibDataWith(
      materialType = Some(SierraMaterialType(code = materialTypeCode.toString)),
      varFields = varFields
    )
  }

  private def create776subfieldsWith(ids: List[String]): List[VarField] =
    ids.map {
      idString =>
        VarField(
          marcTag = "776",
          subfields = List(
            Subfield(tag = "w", content = idString)
          )
        )
    }

  private def create962subfieldsWith(urls: List[String]): List[VarField] =
    urls.map {
      url =>
        VarField(
          marcTag = "962",
          subfields = List(
            Subfield(tag = "u", content = url)
          )
        )
    }

  private def create962subfieldsForWellcomeImageUrl(miroId: String) =
    create962subfieldsWith(
      urls = List(s"http://wellcomeimages.org/indexplus/image/$miroId.html")
    )

  private def create089subfieldsWith(miroIds: List[String]): List[VarField] =
    miroIds.map {
      miroId =>
        VarField(
          marcTag = "089",
          subfields = List(
            Subfield(tag = "a", content = miroId)
          )
        )
    }

  private def physicalAndDigitalSierraMergeCandidate(
    bibNumber: String
  ): List[MergeCandidate[IdState.Identifiable]] =
    List(
      MergeCandidate(
        identifier = SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          ontologyType = "Work",
          value = bibNumber
        ),
        reason = "Physical/digitised Sierra work"
      )
    )

  private def miroMergeCandidate(
    miroID: String,
    reason: String = "Miro/Sierra work"
  ): List[MergeCandidate[IdState.Identifiable]] =
    List(
      MergeCandidate(
        identifier = SourceIdentifier(
          identifierType = IdentifierType.MiroImageNumber,
          ontologyType = "Work",
          value = miroID
        ),
        reason = reason
      )
    )
}
