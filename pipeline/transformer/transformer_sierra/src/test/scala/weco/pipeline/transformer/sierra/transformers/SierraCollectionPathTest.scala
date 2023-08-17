package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.CollectionPath
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraCollectionPathTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {
  info(
    """
     A collectionPath should only be created for documents that exist within a hierarchy.
       i.e. it is a parent (with a 774 field) or a child (with a 773 field).

     Whether parent or child, a document can only take part in the hierarchy if it
       has a 001 field allowing it to be referred to in 773/774 fields in other documents.

     In order to represent a relationship from child to parent,
       the 773 field must contain an identifier,
       and that identifier must refer to a different document.

     Simply the presence of a 774 field signifies that this document is a parent of something.

     The actual content of a 774 field is not important, as the Relation Embedder only
       understands paths ending at _this_ document, and cannot insert children
       based on data from the parent.

    """
  )

  describe("choosing not to create a collectionPath") {
    it("returns None, if there are no 773 or 774 fields") {
      val varFields = List(
        VarField(
          marcTag = Some("999"),
          content = Some("banana")
        )
      )
      getCollectionPath(varFields) shouldBe None
    }

    it("returns None, if no 773/774 fields have the $w subfield") {
      forAll(
        Table(
          "marcTag",
          "773",
          "774"
        )
      ) {
        (marcTag) =>
          val varFields = List(
            VarField(
              marcTag = Some(marcTag),
              content = Some("title-only reference to the other document")
            )
          )
          getCollectionPath(varFields) shouldBe None
      }
    }

    it("returns None, if an otherwise matching document has no 001 field") {
      forAll(
        Table(
          "marcTag",
          "773",
          "774"
        )
      ) {
        marcTag =>
          val varFields = List(
            VarField(
              marcTag = Some(marcTag),
              subfields = List(
                Subfield(tag = "t", content = "A Constituent"),
                Subfield(
                  tag = "w",
                  content =
                    "This value does not matter, it just matters that it exists"
                )
              )
            )
          )
          getCollectionPath(varFields) shouldBe None
      }
    }

    it(
      "returns None if a 773 field is present, but it refers to itself"
    ) {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "Myself"),
            Subfield(tag = "w", content = "56789i")
          )
        )
      )
      getCollectionPath(varFields) shouldBe None
    }
  }

  describe("choosing to create a collectionPath") {
    it("returns the document's own id when a 774 field is found") {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("12345i")
        ),
        VarField(
          marcTag = Some("774"),
          subfields = List(
            Subfield(tag = "t", content = "A Constituent"),
            Subfield(
              tag = "w",
              content =
                "This value does not matter, it just matters that it exists"
            )
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(path = "12345i")
    }

    it(
      "returns the document's own id when a 774 field is found alongside a self-referential 773"
    ) {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("12345i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "Myself"),
            Subfield(tag = "w", content = "12345i")
          )
        ),
        VarField(
          marcTag = Some("774"),
          subfields = List(
            Subfield(tag = "t", content = "A Constituent"),
            Subfield(
              tag = "w",
              content =
                "This value does not matter, it just matters that it exists"
            )
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(path = "12345i")
    }

    it(
      "constructs a value from a 773 field, when both 773 and 774 are present"
    ) {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "A Host"),
            Subfield(tag = "w", content = "12345i")
          )
        ),
        VarField(
          marcTag = Some("774"),
          subfields = List(
            Subfield(tag = "t", content = "A Constituent"),
            Subfield(
              tag = "w",
              content =
                "This value does not matter, it just matters that it exists"
            )
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/56789i"
      )
    }

    it(
      "constructs a value from a 773 field, when no 774 field is present"
    ) {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "A Host"),
            Subfield(tag = "w", content = "12345i")
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/56789i"
      )
    }

  }

  describe("variations in 773 fields") {
    it(
      "constructs a value from a 773 field, consisting of $w and the document's own id"
    ) {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "A Host"),
            Subfield(tag = "w", content = "12345i")
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/56789i"
      )
    }

    it("removes the (Wcat) prefix, from the $w subfield, if present") {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "A Host"),
            Subfield(tag = "w", content = "(Wcat)12345i")
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/56789i"
      )
    }

    it("ignores leading and trailing whitespace in path segments") {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some(" 56789i ")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "A Host"),
            Subfield(tag = "w", content = " 12345i ")
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/56789i"
      )
    }

    it("constructs a value from a 773 field, including $g if present") {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "A Host"),
            Subfield(tag = "w", content = "(Wcat)12345i"),
            Subfield(tag = "g", content = "page 4")
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/page_4_56789i"
      )
    }

    it(
      "constructs a value from the correct 773 field, even when there are multiple"
    ) {
      val varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "Not this one")
          )
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "Or this one"),
            Subfield(tag = "g", content = "Not this page either")
          )
        ),
        VarField(
          marcTag = Some("773"),
          subfields = List(
            Subfield(tag = "t", content = "This one"),
            Subfield(tag = "w", content = "(Wcat)12345i"),
            Subfield(tag = "g", content = "vol. 4")
          )
        )
      )
      getCollectionPath(varFields).get shouldBe CollectionPath(
        path = "12345i/vol_4_56789i"
      )
    }
  }
  private def getCollectionPath(
    varFields: List[VarField]
  ): Option[CollectionPath] =
    SierraCollectionPath(createSierraBibDataWith(varFields = varFields))
}
