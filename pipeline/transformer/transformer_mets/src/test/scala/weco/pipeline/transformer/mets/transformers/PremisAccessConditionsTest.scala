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
      it("pulls out the copyrightNote for the licence") {
        PremisAccessConditions(inCopyrightRightsMD).copyrightNote shouldBe Some(
          "In copyright"
        )
      }
      it("pulls out the rightsGrantedNote for the access status") {
        PremisAccessConditions(
          inCopyrightRightsMD
        ).useRightsGrantedNote shouldBe Some("Open")
      }

    }
  }
}
