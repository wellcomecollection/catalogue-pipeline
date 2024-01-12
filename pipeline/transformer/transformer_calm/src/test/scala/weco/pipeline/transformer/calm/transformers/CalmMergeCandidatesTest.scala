package weco.pipeline.transformer.calm.transformers

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
    with CalmRecordGenerators {

  it("creates a Sierra mergeCandidate from the BNumber field") {
    val bnumber = "b12345672"
    val record = createCalmRecordWith(
      "BNumber" -> bnumber
    )
    val mergeCandidates = CalmMergeCandidates(record)

    mergeCandidates should contain only MergeCandidate(
      identifier = SourceIdentifier(
        identifierType = IdentifierType.SierraSystemNumber,
        ontologyType = "Work",
        value = bnumber
      ),
      reason = "CALM/Sierra harvest work"
    )
  }

  describe("creating a METS mergeCandidate from the SDB_URL") {
    describe("successful creation") {
      info("SDB_URL has been repurposed to record the Archivematica UUID")
      info(
        "See: https://app.gitbook.com/o/-LumfFcEMKx4gYXKAZTQ/s/HcPN6OwpaxdCucwfmiAz/metadata-fields/superseded-fields#sdb_ref-sdb_type-sdb_url"
      )
      it("creates a mergeCandidate from a UUID") {}
      it("ignores leading and trailing whitespace") {}

    }

    it(
      "does not create a METS mergeCandidate from a URL in the SDB_URL field"
    ) {
      info(
        "There are many records in which this field is still populated with defunct links to SDB"
      )
      info(
        "These links are fully-qualified URLs, starting with http://"
      )
    }
    it("does not create a METS mergeCandidate from an empty string") {}
    it("does not create a METS mergeCandidate from an unexpected value") {
      info("the only expected values are URLs and UUIDs")
      info("and mergeCandidates are only to be created from UUIDs")
      info("anything else in this field is a mistake")
    }

  }
}
