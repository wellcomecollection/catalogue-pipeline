package weco.pipeline.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.generators.MiroRecordGenerators

class MiroItemsTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with MiroRecordGenerators {
  val transformer = new MiroItems {}

  describe("getItems") {
    it("extracts an unidentifiable item") {
      val items = transformer.getItems(
        miroRecord = createMiroRecordWith(
          sourceCode = Some("FDN"),
          useRestrictions = Some("CC-0"),
          imageNumber = "B0011308"
        ),
        overrides = MiroSourceOverrides.empty
      )

      items shouldBe List(
        Item(
          id = IdState.Unidentifiable,
          locations = List(
            DigitalLocation(
              url =
                "https://iiif.wellcomecollection.org/image/B0011308/info.json",
              locationType = LocationType.IIIFImageAPI,
              license = Some(License.CC0),
              credit = Some("Ezra Feilden"),
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.Open
                )
              )
            )
          )
        )
      )
    }
  }
}
