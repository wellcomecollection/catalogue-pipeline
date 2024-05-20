package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.CollectionPath
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{
  MarcControlField,
  MarcField,
  MarcSubfield
}

class MarcCollectionPathTest extends AnyFunSpec with Matchers with LoneElement {
  describe("Extracting host / constituent unit from MARC 773 & 774") {
    info("https://www.loc.gov/marc/bibliographic/bd773.html")
    info("https://www.loc.gov/marc/bibliographic/bd774.html")
    info("""
        |A collectionPath should only be created for documents that exist within a hierarchy.
        |       i.e. it is a parent (with a 774 field) or a child (with a 773 field).
        |
        |     Whether parent or child, a document can only take part in the hierarchy if it
        |       has a 001 field allowing it to be referred to in 773/774 fields in other documents.
        |
        |     In order to represent a relationship from child to parent,
        |       the 773 field must contain an identifier,
        |       and that identifier must refer to a different document.
        |
        |     Simply the presence of a 774 field signifies that this document is a parent of something.
        |
        |     The actual content of a 774 field is not important, as the Relation Embedder only
        |       understands paths ending at _this_ document, and cannot insert children
        |       based on data from the parent.
        |""".stripMargin)

    describe(
      "for a MARC record with a field value for 773ǂw, 773ǂg and a 001 control field (leaf node)"
    ) {

      describe("without 773ǂg") {
        it(
          "creates a collectionPath"
        ) {
          val recordId = "record_id"
          val parentId = "parent_id"
          val record = MarcTestRecord(
            controlFields = Seq(MarcControlField("001", recordId)),
            fields = Seq(
              MarcField(
                "773",
                subfields = Seq(
                  MarcSubfield("w", parentId)
                )
              )
            )
          )

          MarcCollectionPath(record).get shouldBe CollectionPath(
            path = f"$parentId/$recordId",
            label = None
          )
        }
      }

      describe("with 773ǂg") {
        it(
          "creates a collectionPath"
        ) {
          val recordId = "record_id"
          val parentId = "parent_id"
          val relatedPart = "Some Qualified Name 1."
          val expectedRelatedPart = "Some_Qualified_Name_1"

          val record = MarcTestRecord(
            controlFields = Seq(MarcControlField("001", recordId)),
            fields = Seq(
              MarcField(
                "773",
                subfields = Seq(
                  MarcSubfield("w", parentId),
                  MarcSubfield("g", relatedPart)
                )
              )
            )
          )

          MarcCollectionPath(record).get shouldBe CollectionPath(
            path = f"$parentId/${expectedRelatedPart}_$recordId",
            label = None
          )
        }
      }

      it(
        "creates a collectionPath with trimmed IDs"
      ) {
        val recordId = " record_id "
        val parentId = " parent_id "
        val record = MarcTestRecord(
          controlFields = Seq(MarcControlField("001", recordId)),
          fields = Seq(
            MarcField(
              "773",
              subfields = Seq(
                MarcSubfield("w", parentId)
              )
            )
          )
        )

        MarcCollectionPath(record).get shouldBe CollectionPath(
          path = f"${parentId.trim}/${recordId.trim}",
          label = None
        )
      }

      it(
        "does NOT create a collectionPath where the parent is self referential"
      ) {
        val recordId, parentId = "record_id"
        val record = MarcTestRecord(
          controlFields = Seq(MarcControlField("001", recordId)),
          fields = Seq(
            MarcField(
              "773",
              subfields = Seq(
                MarcSubfield("w", parentId)
              )
            )
          )
        )

        MarcCollectionPath(record) shouldBe None
      }
    }

    describe(
      "for a MARC record with a field value for 774 and a 001 control field (root node)"
    ) {
      it("creates a collectionPath") {
        val recordId = "record_id"
        val record = MarcTestRecord(
          controlFields = Seq(MarcControlField("001", recordId)),
          fields = Seq(
            MarcField(
              "774",
              subfields = Seq(
                MarcSubfield(
                  "t",
                  "A Constituent (whose value does not matter)"
                ),
                MarcSubfield(
                  "w",
                  "This value does not matter, it just matters that it exists"
                )
              )
            )
          )
        )

        MarcCollectionPath(record).get shouldBe CollectionPath(
          path = recordId,
          label = None
        )
      }
    }

    describe(
      "for a MARC record with a field value for 773ǂw, 774 and a 001 control field (branch node)"
    ) {
      it("creates a collectionPath") {
        val recordId = "record_id"
        val parentId = "parent_id"

        val record = MarcTestRecord(
          controlFields = Seq(MarcControlField("001", recordId)),
          fields = Seq(
            MarcField(
              "773",
              subfields = Seq(
                MarcSubfield("w", parentId)
              )
            ),
            MarcField(
              "774",
              subfields = Seq(
                MarcSubfield(
                  "t",
                  "A Constituent (whose value does not matter)"
                ),
                MarcSubfield(
                  "w",
                  "This value does not matter, it just matters that it exists"
                )
              )
            )
          )
        )

        MarcCollectionPath(record).get shouldBe CollectionPath(
          path = f"$parentId/$recordId",
          label = None
        )
      }
    }

    describe("for a MARC record with no 001 control field") {
      it(
        "does NOT create a collectionPath (leaf node)"
      ) {
        val parentId = "parent_id"
        val record = MarcTestRecord(
          fields = Seq(
            MarcField(
              "773",
              subfields = Seq(
                MarcSubfield("w", parentId)
              )
            )
          )
        )

        MarcCollectionPath(record) shouldBe None
      }
    }
  }
}
