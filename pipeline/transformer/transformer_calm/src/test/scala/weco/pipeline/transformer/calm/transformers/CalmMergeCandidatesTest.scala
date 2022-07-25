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
}
