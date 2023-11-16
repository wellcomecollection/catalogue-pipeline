package weco.pipeline.transformer.mets.transformer.models
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.MetsGenerators

class TitlePageIdTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with MetsGenerators {

  describe("finding the title page id") {

    it("resolves the smLink to find the physical ID") {
      val root =
        <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
        <mets:structMap TYPE="LOGICAL">
          <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
            <mets:div ID="LOG_0001" TYPE="CoverFrontOutside"/>
            <mets:div ID="LOG_0002" TYPE="TitlePage"/>
            <mets:div ID="LOG_0003" TYPE="CoverBackOutside"/>
          </mets:div>
        </mets:structMap>
        <mets:structLink>
          <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
        </mets:structLink>
      </mets:mets>

      TitlePageId(root).get shouldBe "PHYS_0002"
    }

    it(
      "chooses the first title page from the logical structmap in document order"
    ) {
      val root =
        <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
        <mets:structMap TYPE="LOGICAL">
          <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
            <mets:div ID="LOG_0001" TYPE="CoverFrontOutside"/>
            <mets:div ID="LOG_0002" TYPE="TitlePage"/>
            <mets:div ID="LOG_0003" TYPE="TitlePage"/>
          </mets:div>
        </mets:structMap>
        <mets:structLink>
          <mets:smLink xlink:from="LOG_0003" xlink:to="PHYS_0003"/>
          <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
        </mets:structLink>
      </mets:mets>

      TitlePageId(root).get shouldBe "PHYS_0002"
    }

    it(
      "returns None if there is no TitlePage"
    ) {
      val root =
        <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
          <mets:structMap TYPE="LOGICAL">
            <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
              <mets:div ID="LOG_0001" TYPE="CoverFrontOutside"/>
            </mets:div>
          </mets:structMap>
          <mets:structLink>
            <mets:smLink xlink:from="LOG_0003" xlink:to="PHYS_0003"/>
            <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
          </mets:structLink>
        </mets:mets>

      TitlePageId(root) shouldBe None
    }

    it(
      "returns None if the corresponding smLink is missing"
    ) {
      val root =
        <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
          <mets:structMap TYPE="LOGICAL">
            <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
              <mets:div ID="LOG_0001" TYPE="CoverFrontOutside"/>
              <mets:div ID="LOG_9999" TYPE="TitlePage"/>
            </mets:div>
          </mets:structMap>
          <mets:structLink>
            <mets:smLink xlink:from="LOG_0003" xlink:to="PHYS_0003"/>
            <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
          </mets:structLink>
        </mets:mets>

      TitlePageId(root) shouldBe None
    }
  }

}
