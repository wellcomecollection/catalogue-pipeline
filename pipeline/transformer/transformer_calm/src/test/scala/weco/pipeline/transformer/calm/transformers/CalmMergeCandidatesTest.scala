package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
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
    val bnumber = "b1234567"
    val record = createCalmRecordWith(
      "BNumber" -> bnumber
    )
    val mergeCandidates = CalmMergeCandidates(record)

    mergeCandidates should contain only MergeCandidate(
      IdState.Identifiable(
        SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          ontologyType = "Work",
          value = bnumber
        )
      )
    )
  }

  it("creates Miro mergeCandidates from Wheels fields") {
    val miroIds = List("M0000001", "M0000002", "M0000003")
    val record = createCalmRecordWith(
      miroIds.map(id => "Wheels" -> id): _*
    )
    val mergeCandidates = CalmMergeCandidates(record)

    mergeCandidates should contain allElementsOf miroIds.map(
      id =>
        MergeCandidate(
          IdState.Identifiable(
            SourceIdentifier(
              identifierType = IdentifierType.MiroImageNumber,
              ontologyType = "Work",
              value = id
            )
          )
      )
    )
  }

  it("returns both Miro and Sierra mergeCandidates") {
    val bnumber = "b1234567"
    val miroIds = List("M0000001", "M0000002")
    val record = createCalmRecordWith(
      ("BNumber" -> bnumber) ::
        miroIds.map(id => "Wheels" -> id): _*
    )
    val mergeCandidates = CalmMergeCandidates(record)

    mergeCandidates should contain(
      MergeCandidate(
        IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.SierraSystemNumber,
            ontologyType = "Work",
            value = bnumber
          )
        )
      )
    )
    mergeCandidates should contain allElementsOf miroIds.map(
      id =>
        MergeCandidate(
          IdState.Identifiable(
            SourceIdentifier(
              identifierType = IdentifierType.MiroImageNumber,
              ontologyType = "Work",
              value = id
            )
          )
      )
    )
  }
}
