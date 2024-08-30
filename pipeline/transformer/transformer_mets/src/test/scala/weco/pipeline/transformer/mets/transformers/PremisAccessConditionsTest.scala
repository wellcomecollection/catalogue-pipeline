package weco.pipeline.transformer.mets.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{AccessStatus, License}
import weco.pipeline.transformer.mets.generators.PremisAccessConditionsGenerators

class PremisAccessConditionsTest
    extends AnyFunSpec
    with Matchers
    with PremisAccessConditionsGenerators {
  describe("Access conditions derived from premis rights statements") {
    describe("conversion to controlled vocabulary") {
      // Based on the limited examples I have, the existing controlled vocabulary translation works.
      // Also, this appears to be the correct place to pull the values from for now.
      it("translates the rightsGrantedNote into AccessStatus") {
        PremisAccessConditions(
          None,
          Some("Open")
        ).parse.right.get.accessStatus.get shouldBe AccessStatus.Open
      }

      it("translates the copyrightNote into a Licence") {
        PremisAccessConditions(
          Some("In Copyright"),
          None
        ).parse.right.get.licence.get shouldBe License.InCopyright
      }

      it("has no access conditions if none are given") {
        val conditions = PremisAccessConditions(
          None,
          None
        ).parse.right.get
        conditions.accessStatus shouldBe None
        conditions.licence shouldBe None
      }

      it("fails if the copyrightNote is something unexpected") {
        PremisAccessConditions(
          Some("Yow! Legally-imposed CULTURE-reduction is CABBAGE-BRAINED!"),
          None
        ).parse shouldBe a[Left[_, _]]
      }

      it("fails if the accessStatus is something unexpected") {
        PremisAccessConditions(
          None,
          Some("flexible friend")
        ).parse shouldBe a[Left[_, _]]
      }
    }

    describe("extracting values from a rightsMD section") {
      it("pulls out the copyrightNote where rightsBasis is 'Copyright'") {
        PremisAccessConditions(
          openInCopyrightRightsMD
        ).copyrightNote shouldBe Some(
          "In copyright"
        )
      }

      it("pulls out the licenceNote where rightsBasis is 'License'") {
        PremisAccessConditions(
          openCCBYNCRightsMD
        ).copyrightNote shouldBe Some("CC-BY-NC")
      }

      it("pulls out the copyrightNote where rightsBasis is not specified") {
        PremisAccessConditions(
          openMixedCCBYNCWithCopyrightRightsMD
        ).copyrightNote shouldBe Some(
          "In copyright"
        )
      }

      it("pulls out the rightsGrantedNote for the access status") {
        PremisAccessConditions(
          openInCopyrightRightsMD
        ).useRightsGrantedNote shouldBe Some("Open")
      }

      it("pulls out the copy for the access status") {
        PremisAccessConditions(
          openInCopyrightRightsMD
        ).useRightsGrantedNote shouldBe Some("Open")
      }

      it("creates empty accessConditions if the relevant fields are absent") {
        val conditions = PremisAccessConditions(
          emptyRightsMD
        )
        conditions.copyrightNote shouldBe None
        conditions.useRightsGrantedNote shouldBe None
      }

      it("ignores a rightsGrantedNote if it is not for the 'use' act") {
        // act could contain various things.  "use" is the one we care about
        // https://docs.rockarch.org/premis-rights-guidelines/guidelines#act-rightsgranted
        val conditions = PremisAccessConditions(
          rightsMDWith(
            rightsGranted = Seq(<premis:rightsGranted>
              <premis:act>disseminate</premis:act>
              <premis:rightsGrantedNote>Open</premis:rightsGrantedNote>
            </premis:rightsGranted>)
          )
        )
        conditions.useRightsGrantedNote shouldBe None
      }

      it(
        "finds the correct a rightsGrantedNote if there are more than one"
      ) {
        // act could contain various things.  "use" is the one we care about
        // https://docs.rockarch.org/premis-rights-guidelines/guidelines#act-rightsgranted
        val conditions = PremisAccessConditions(
          rightsMDWith(
            rightsGranted = Seq(
              <premis:rightsGranted>
                <premis:act>replicate</premis:act>
                <premis:rightsGrantedNote>Open</premis:rightsGrantedNote>
              </premis:rightsGranted>,
              <premis:rightsGranted>
                <premis:act>use</premis:act>
                <premis:rightsGrantedNote>Open with advisory</premis:rightsGrantedNote>
              </premis:rightsGranted>
            )
          )
        )
        conditions.useRightsGrantedNote.get shouldBe "Open with advisory"
      }
    }
  }
}
