package weco.pipeline.transformer.ebsco

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.work.WorkData
import weco.catalogue.source_model.ebsco.EbscoUpdatedSourceData
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryStore

class EbscoTransformerTest
    extends AnyFunSpec
    with S3ObjectLocationGenerators
    with EitherValues
    with Matchers {

  describe("a minimal XML record") {
    it("generates a Work with a sourceIdentifier") {
      info("at minimum, a Work from an XML record needs an id and a title")
      val location = createS3ObjectLocation

      val record =
        <record xmlns="http://www.loc.gov/MARC21/slim">
            <controlfield tag="001">3PaDhRp</controlfield>
            <datafield tag ="245">
              <subfield code="a">matacologian</subfield>
            </datafield>
          </record>

      val transformer = new EbscoTransformer(
        new MemoryStore[S3ObjectLocation, String](
          Map(location -> record.toString())
        )
      )

      val result =
        transformer.apply("3PaDhRp", EbscoUpdatedSourceData(location), 20240401)
      result shouldBe a[Right[_, _]]
      val work = result.value

      work.state.sourceIdentifier.value shouldBe "3PaDhRp"
      work.data should equal(
        WorkData[DataState.Unidentified](title = Some("matacologian"))
      )
    }
  }
}
