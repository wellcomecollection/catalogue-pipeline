package weco.pipeline.transformer.mets.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MetsTitleTest extends AnyFunSpec with Matchers with EitherValues {
  it("finds the title") {
    val elem =
      <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
        <mets:dmdSec ID="DMDLOG_0000">
          <mets:mdWrap MDTYPE="MODS">
            <mets:xmlData>
              <mods:mods>
                <mods:titleInfo>
                  <mods:title>[Report 1942] /</mods:title>
                </mods:titleInfo>
              </mods:mods>
            </mets:xmlData>
          </mets:mdWrap>
        </mets:dmdSec>
      </mets:mets>

    MetsTitle(elem).value shouldBe "[Report 1942] /"
  }

  it("fails if there is no mods:title element") {
    val elem =
      <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
        <mets:dmdSec ID="DMDLOG_0000">
          <mets:mdWrap MDTYPE="MODS">
            <mets:xmlData>
              <mods:mods>
                <mods:titleInfo>
                </mods:titleInfo>
              </mods:mods>
            </mets:xmlData>
          </mets:mdWrap>
        </mets:dmdSec>
      </mets:mets>

    MetsTitle(elem).left.value.getMessage shouldBe "Could not parse title from METS XML"
  }
}
