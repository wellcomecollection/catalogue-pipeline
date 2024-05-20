package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmAlternativeTitlesTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators {
  it("doesn't find any alternative titles on an empty record") {
    val record = createCalmRecord

    CalmAlternativeTitles(record) shouldBe empty
  }

  it("gets an alternative title") {
    // This example is based on d7342687-9d34-479f-8214-48f26b0870f3
    val record = createCalmRecordWith(
      ("Alternative_Title", "Disc 5_Final Transcripts")
    )

    CalmAlternativeTitles(record) shouldBe List("Disc 5_Final Transcripts")
  }
}
