package weco.pipeline.transformer.calm.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.MergeCandidate
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmMergeCandidatesTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators
    with LoneElement {
  describe("creating a Sierra mergeCandidate from the BNumber") {
    describe("successful creation") {
      it("creates a Sierra mergeCandidate from the BNumber field") {
        val bnumber = "b12345672"
        val record = createCalmRecordWith(
          "BNumber" -> bnumber
        )
        val mergeCandidates = CalmMergeCandidates(record)

        mergeCandidates.loneElement shouldBe MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.SierraSystemNumber,
            ontologyType = "Work",
            value = bnumber
          ),
          reason = "CALM/Sierra harvest work"
        )
      }
    }

    describe("invalid BNumber values") {
      it("does not create a Sierra mergeCandidate from an empty string") {
        val record = createCalmRecordWith(
          "BNumber" -> ""
        )
        val mergeCandidates = CalmMergeCandidates(record)
        mergeCandidates shouldBe empty
      }
      it("does not create a Sierra mergeCandidate from an unexpected value") {
        val record = createCalmRecordWith(
          "BNumber" -> "The called party is a person who (or device that) answers a telephone call."
        )
        val mergeCandidates = CalmMergeCandidates(record)
        mergeCandidates shouldBe empty
      }
    }
  }

  describe("creating a METS mergeCandidate from the RefNo") {
    info(
      "Although SDB_URL has been repurposed to record the Archivematica UUID"
    )
    info(
      "(See: https://app.gitbook.com/o/-LumfFcEMKx4gYXKAZTQ/s/HcPN6OwpaxdCucwfmiAz/metadata-fields/superseded-fields#sdb_ref-sdb_type-sdb_url)"
    )
    info("Archivematica UUIDs are not stable, and change with every ingest.")
    info(
      "Therefore, the most stable identifier that is common to the two records is the RefNo."
    )
    info("Following the same pattern as Sierra bnumbers,")
    info(
      "the Refno serves as the identifier for the METS document, in its own namespace"
    )
    describe("successful creation") {
      it("creates a mergeCandidate from the RefNo") {
        val refno = "CAFE/FO/OD"
        val record = createCalmRecordWith(
          "RefNo" -> refno
        )
        val mergeCandidates = CalmMergeCandidates(record)

        mergeCandidates.loneElement shouldBe MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.CalmRefNo,
            ontologyType = "Work",
            value = refno
          ),
          reason = "Archivematica work"
        )
      }
      it("ignores leading and trailing whitespace") {
        val uuid = "CAFE/FO/OD"
        val record = createCalmRecordWith(
          "RefNo" -> s" \n $uuid \t\r   "
        )
        val mergeCandidates = CalmMergeCandidates(record)

        mergeCandidates.loneElement shouldBe MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.CalmRefNo,
            ontologyType = "Work",
            value = uuid
          ),
          reason = "Archivematica work"
        )

      }

    }
    describe("invalid SDB_URL values") {
      it(
        "does not create a METS mergeCandidate from a URL in the SDB_URL field"
      ) {
        info(
          "There are many records in which this field is still populated with defunct links to SDB"
        )
        info(
          "These links are fully-qualified URLs, starting with http://"
        )
        info("These links are meaningless and should be ignored")
        val record = createCalmRecordWith(
          "SDB_URL" -> "http://example.com/baadf00d-baad-cafe-f00d-baadcafef00d"
        )
        val mergeCandidates = CalmMergeCandidates(record)
        mergeCandidates shouldBe empty
      }

      it("does not create a METS mergeCandidate from an empty string") {
        val record = createCalmRecordWith(
          "SDB_URL" -> ""
        )
        val mergeCandidates = CalmMergeCandidates(record)
        mergeCandidates shouldBe empty
      }
      it("does not create a METS mergeCandidate from an unexpected value") {
        info("the only expected values are URLs and UUIDs")
        info("and mergeCandidates are only to be created from UUIDs")
        info("anything else in this field is a mistake")
        val record = createCalmRecordWith(
          "SDB_URL" -> "A B-type subdwarf (sdB) is a kind of subdwarf star with spectral type B. "
        )
        val mergeCandidates = CalmMergeCandidates(record)
        mergeCandidates shouldBe empty
      }
    }
  }

  describe("creating merge candidates of both types") {
    it("can merge with both a Sierra and a METS candidate") {
      val refno = "DOO/DF/OOD"
      val bnumber = "b12345672"
      val record = createCalmRecordWith(
        "RefNo" -> refno,
        "BNumber" -> bnumber
      )

      val mergeCandidates = CalmMergeCandidates(record)

      mergeCandidates should contain theSameElementsAs Seq(
        MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.CalmRefNo,
            ontologyType = "Work",
            value = refno
          ),
          reason = "Archivematica work"
        ),
        MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.SierraSystemNumber,
            ontologyType = "Work",
            value = bnumber
          ),
          reason = "CALM/Sierra harvest work"
        )
      )
    }
    it("can successfully create a METS candidate when faced with bad BNumber") {
      val refno = "GOOD/FO/OD"
      val record = createCalmRecordWith(
        "RefNo" -> refno,
        "BNumber" -> "AAAAAAAAAAAAAAA!"
      )

      val mergeCandidates = CalmMergeCandidates(record)

      mergeCandidates.loneElement shouldBe
        MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.CalmRefNo,
            ontologyType = "Work",
            value = refno
          ),
          reason = "Archivematica work"
        )
    }
    it(
      "can successfully create a Sierra candidate when faced with bad SDB_URL"
    ) {
      val bnumber = "b12345672"
      val record = createCalmRecordWith(
        "SDB_URL" -> "NoNoNoNoNoNoNo!!!!!!!",
        "BNumber" -> bnumber
      )

      val mergeCandidates = CalmMergeCandidates(record)

      mergeCandidates.loneElement shouldBe
        MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType.SierraSystemNumber,
            ontologyType = "Work",
            value = bnumber
          ),
          reason = "CALM/Sierra harvest work"
        )
    }
  }
}
