package weco.pipeline.transformer.mets.transformer

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessStatus,
  DigitalLocation,
  License
}
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.ArchivematicaMetsGenerators
import weco.pipeline.transformer.mets.transformers.MetsAccessConditions

import java.time.Instant

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

      it("extracts the metsIdentifier from a premis objectIdentifier element") {
        ArchivematicaMetsXML(
          archivematicaMetsWith(metsIdentifier =
            "baadf00d-beef-cafe-f00d-beefcafef00d"
          )
        ).metsIdentifier.right.get shouldBe
          "baadf00d-beef-cafe-f00d-beefcafef00d"
      }

      it("uses the recordIdentifier to determine the location") {
        val xml = ArchivematicaMetsXML(
          archivematicaMetsWith(recordIdentifier = "BA/AD/FO/OD")
        )
        val metsWork = InvisibleMetsData(
          root = xml,
          filesRoot = xml,
          version = 1,
          modifiedTime = Instant.now()
        ).right.get.toWork
        metsWork.data.items.head.locations.head
          .asInstanceOf[DigitalLocation]
          .url shouldBe "https://iiif.wellcomecollection.org/presentation/BA/AD/FO/OD"
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
