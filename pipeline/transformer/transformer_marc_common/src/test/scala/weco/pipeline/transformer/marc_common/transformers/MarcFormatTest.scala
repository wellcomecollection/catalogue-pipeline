package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.Format.{EBooks, EJournals}
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.MarcControlField

class MarcFormatTest extends AnyFunSpec with Matchers with OptionValues with TableDrivenPropertyChecks{
  describe(
    "Extracting format information from MARC Leader/07, 006/06 & 008 material specific details"
  ) {
    info("https://www.loc.gov/marc/bibliographic/bdleader.html")
    info("https://www.loc.gov/marc/bibliographic/bd006.html")
    info("https://www.loc.gov/marc/bibliographic/bd008s.html")


    describe("with a MARC leader and 006 control field") {
      forAll(
        Table(
          ("leader", "006", "expectedFormat"),
          // 'as' in Leader/06 & 07  indicates 'language material' and 'serial'
          // 'o' in 006/06 indicates 'online'
          ("00000cas a22000003a 4500", "m\\\\\\\\\\o\\\\d\\\\||||||", Some(EJournals)),
          // 'am' in Leader/06 & 07  indicates 'language material' and 'monograph'
          // 'o' in 006/06 indicates 'online'
          ("00000cam a22000003a 4500", "m\\\\\\\\\\o\\\\d\\\\||||||", Some(EBooks)),
          // 'am' in Leader/06 & 07  indicates 'language material' and 'monograph'
          // 'f' in 006/06 indicates 'braille'
          ("00000cam a22000003a 4500", "m\\\\\\\\\\f\\\\d\\\\||||||", None),
          // 'ac' in Leader/06 & 07  indicates 'language material' and 'collection'
          // 'o' in 006/06 is non-sensical without a mapping to material type
          ("00000cac a22000003a 4500", "m\\\\\\\\\\o\\\\d\\\\||||||", None),

        )
      ) {
        (leader, controlField006, expectedFormat) =>
          it(
            s"returns $expectedFormat when Leader/06 & 07 is '${leader.slice(6, 8)}' and 006/06 is '${controlField006.slice(6, 7)}'"
          ) {
            MarcFormat(MarcTestRecord(
              leader = leader,
              controlFields = Seq(
                MarcControlField("006", controlField006)
              )
            )) shouldBe expectedFormat
          }
      }
    }
  }
}
