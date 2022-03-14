package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.CollectionPath
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc
import weco.sierra.models.marc.{Subfield, VarField}

class SierraCollectionPathTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {

  it("returns None, if there are no 773 or 774 fields") {
    val varFields = List(
      VarField(
        marcTag = Some("999"),
        content = Some("banana")
      )
    )
    getCollectionPath(varFields) shouldBe None
  }

  it("returns None, if a relevant field does not have the $w subfield") {
    forAll(
      Table(
        "marcTag",
        "773",
        "774"
      )) { (marcTag) =>
      val varFields = List(
        VarField(
          marcTag = Some(marcTag),
          content = Some("banana")
        )
      )
      getCollectionPath(varFields) shouldBe None
    }
  }

  it("returns the document's own id for a 774 field") {
    val varFields = List(
      VarField(
        marcTag = Some("001"),
        content = Some("12345i")
      ),
      VarField(
        marcTag = Some("774"),
        subfields = List(
          Subfield(tag = "t", content = "A Constituent"),
          Subfield(tag = "w", content = "(Wcat)28931i")
        )
      )
    )
    getCollectionPath(varFields) shouldBe CollectionPath(path = "12345i")
  }

  it(
    "constructs a value from a 773 field, consisting of $w, $g and the document's own id") {
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
    getCollectionPath(varFields) shouldBe CollectionPath(
      path = "12345i/page4_56789i")
    // CollectionPath is path (ids) and label (concat the $g and title?),
    // In CALM records, they match,
    // In TEI records, the path object does not necessarily have a label
    //
    // 001   28914i
    // 245 00 Charing Cross Hospital: a portrait of house surgeons. Photograph, 1906.
    // 773   Basil Hood. Photograph album,|gpage 5.|w(Wcat)9175i
    // path: 9175i/page_5_28914i
    // label: Basil Hood. Photograph album/page 5.
  }

  it("constructs a value from a 773 field, without a $g") {
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
    getCollectionPath(varFields) shouldBe CollectionPath(path = "12345i/56789i")
  }

  it("removes field-final punctuation from the $t subfield") {
    fail()
  }

  private def getCollectionPath(
    varFields: List[VarField]): Option[CollectionPath] =
    SierraCollectionPath(createSierraBibDataWith(varFields = varFields))
}
