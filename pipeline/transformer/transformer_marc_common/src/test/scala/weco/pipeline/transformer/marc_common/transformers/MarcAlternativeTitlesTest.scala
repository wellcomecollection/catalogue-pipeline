package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcAlternativeTitlesTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  describe("extracting alternative titles from 240, 130, and 246 fields") {
    forAll(
      Table(
        "tag",
        "240",
        "130",
        "246"
      )
    ) {
      tag =>
        it(s"extracts an alternative tile from $tag") {
          MarcAlternativeTitles(
            MarcTestRecord(
              fields = Seq(
                MarcField(
                  marcTag = tag,
                  subfields = Seq(MarcSubfield(tag = "a", content = "mafeesh"))
                )
              )
            )
          ).loneElement shouldBe "mafeesh"
        }
    }
  }

}
