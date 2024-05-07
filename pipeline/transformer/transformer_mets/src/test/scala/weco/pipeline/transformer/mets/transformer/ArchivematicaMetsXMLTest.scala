package weco.pipeline.transformer.mets.transformer

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{AccessStatus, License}
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.ArchivematicaMetsGenerators
import weco.pipeline.transformer.mets.transformers.MetsAccessConditions

class ArchivematicaMetsXMLTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with ArchivematicaMetsGenerators {
  {
    describe("extracting data from an Archivematica METS file") {
      it("extracts accessConditions from a rightsMD element") {
        ArchivematicaMetsXML(
          archivematicaMetsWith()
        ).accessConditions.right.get shouldBe
          MetsAccessConditions(
            Some(AccessStatus.Open),
            Some(License.InCopyright),
            None
          )

      }

      it("extracts the recordIdentifier from a dublincore identifier element") {
        ArchivematicaMetsXML(
          archivematicaMetsWith(recordIdentifier = "BA/AD/FO/OD")
        ).recordIdentifier.right.get shouldBe
          "BA/AD/FO/OD"
      }

      it("extracts the metsIdentifier from a dublincore identifier element") {
        ArchivematicaMetsXML(
          archivematicaMetsWith(recordIdentifier = "GO/OD/CA/FE")
        ).metsIdentifier.right.get shouldBe
          "GO/OD/CA/FE"
      }

    }

    describe("failure conditions") {
      it("fails if a document has multiple identifiers") {
        ArchivematicaMetsXML(
          archivematicaMetsWithMultipleIdentifiers
        ).recordIdentifier shouldBe a[Left[_, _]]
      }
      it("fails if a document has no rights information") {
        ArchivematicaMetsXML(
          archivematicaMetsWithNoRights
        ).accessConditions shouldBe a[Left[_, _]]
      }

    }
  }
}
