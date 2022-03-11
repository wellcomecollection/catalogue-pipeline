package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.{Relation, SeriesRelation}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraLinksTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {

  it("returns an empty list if there are no relevant MARC tags") {
    val varFields = List(
      VarField(
        marcTag = Some("999"),
        content = Some("banana")
      )
    )
    getLinks(varFields) shouldBe Nil
  }

  it(
    "returns a Series relation for a 440 - Series Statement/Added Entry-Title field") {
    val varFields = List(
      VarField(
        marcTag = Some("440"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(SeriesRelation("A Series"))
  }

  it("returns a Series relation for a 490 - Series Statement field") {
    val varFields = List(
      VarField(
        marcTag = Some("490"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(SeriesRelation("A Series"))
  }

  it("returns a Series relation for a 773 - Host Item Entry field") {
    // In phase one, all relations from parent to child are treated as
    // Series links.
    // This is subject to change in a later phase.
    // 773 fields differ from the others in that the title is in a subfield
    val varFields = List(
      VarField(
        marcTag = "773",
        subfields = List(Subfield(tag = "t", content = "A Series"))
      )
    )
    getLinks(varFields) shouldBe List(SeriesRelation("A Series"))
  }

  it("Extracts the title from the body of a 773 field, if title is absent") {
    // This is not a scenario we expect to encounter, but applying
    // Postel's Law and logging a warning is better than discarding it
    val varFields = List(
      VarField(
        marcTag = Some("773"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(SeriesRelation("A Series"))
  }

  it(
    "returns a Series relation for an 830 - Series Added Entry-Uniform Title field") {
    val varFields = List(
      VarField(
        marcTag = Some("830"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(SeriesRelation("A Series"))
  }

  it("Extracts the title from the 'a' subfield") {
    forAll(
      Table(
        "marcTag",
        "440",
        "490",
        "773",
        "830"
      )) { (marcTag) =>
      val varFields = List(
        VarField(
          marcTag = Some(marcTag),
          subfields = List(Subfield(tag = "a", content = "A Series"))
        )
      )
      getLinks(varFields) shouldBe List(SeriesRelation("A Series"))
    }

  }

  it(
    "returns a list of series relations when multiple relevant MARC fields are present") {
    val varFields = List(
      VarField(
        marcTag = Some("440"),
        content = Some("A Series")
      ),
      VarField(
        marcTag = Some("490"),
        content = Some("Another Series")
      ),
      VarField(
        marcTag = Some("773"),
        subfields = List(Subfield(tag = "t", content = "A Host"))
      ),
      VarField(
        marcTag = Some("830"),
        content = Some("Yet Another Series")
      )
    )
    getLinks(varFields) shouldBe List(
      SeriesRelation("A Series"),
      SeriesRelation("Another Series"),
      SeriesRelation("A Host"),
      SeriesRelation("Yet Another Series"),
    )
  }

  it(
    "returns a list of series relations when the same relevant MARC field is present multiple times") {
    forAll(
      Table(
        "marcTag",
        "440",
        "490",
        "773",
        "830"
      )) { (marcTag) =>
      val varFields = List(
        VarField(
          marcTag = Some(marcTag),
          content = Some("A Series")
        ),
        VarField(
          marcTag = Some(marcTag),
          content = Some("Another Series")
        ),
        VarField(
          marcTag = Some(marcTag),
          subfields = List(Subfield(tag = "t", content = "A Host"))
        ),
        VarField(
          marcTag = Some(marcTag),
          content = Some("Yet Another Series")
        )
      )

      getLinks(varFields) shouldBe List(
        SeriesRelation("A Series"),
        SeriesRelation("Another Series"),
        SeriesRelation("A Host"),
        SeriesRelation("Yet Another Series"),
      )
    }
  }

  it("returns only unique values if there are duplicates") {
    val varFields = List(
      VarField(
        marcTag = Some("440"),
        content = Some("A Series")
      ),
      VarField(
        marcTag = Some("773"),
        subfields = List(Subfield(tag = "t", content = "A Series"))
      ),
      VarField(
        marcTag = Some("830"),
        content = Some("Another Series")
      )
    )
    getLinks(varFields) shouldBe List(
      SeriesRelation("A Series"),
      SeriesRelation("Another Series"),
    )
  }

  it("ignores fields with no content") {
    val varFields = List(
      VarField(
        marcTag = Some("440"),
        content = Some("A Series")
      ),
      VarField(
        marcTag = Some("490"),
        content = Some("")
      ),
      VarField(
        marcTag = Some("490"),
        // This should be filtered by the subfield separator removal logic,
        // resulting in an empty string, so is to be ignored.
        content = Some(" ;")
      ),
      VarField(
        marcTag = Some("830")
      ),
      VarField(
        marcTag = Some("830"),
        content = Some("Another Series")
      )
    )
    getLinks(varFields) shouldBe List(
      SeriesRelation("A Series"),
      SeriesRelation("Another Series"),
    )
  }

  it("ignores subfields that indicate a part name or number") {
    // In phase one, the partName is simply to be ignored.
    // This is subject to change in a later phase.
    val varFields = List(
      VarField(
        marcTag = Some("773"),
        subfields = List(
          Subfield(tag = "t", content = "A Host"),
          Subfield(tag = "w", content = "page 2")
        )
      ),
      VarField(
        marcTag = Some("830"),
        content = Some("A Big Series"),
        subfields = List(
          Subfield(tag = "v", content = "vol. 2")
        )
      )
    )
    getLinks(varFields) shouldBe List(
      SeriesRelation("A Host"),
      SeriesRelation("A Big Series")
    )
  }

  it("trims known separators between field and subfield") {
    // Expand this test as more of these separators are discovered.
    val varFields = List(
      VarField(
        marcTag = Some("830"),
        subfields = List(Subfield(tag = "v", content = "no. 149.")),
        content =
          Some("Published papers; (Wellcome Chemical Research Laboratories) ;")
      )
    )

    getLinks(varFields) shouldBe List(
      SeriesRelation(
        "Published papers; (Wellcome Chemical Research Laboratories)")
    )
  }

  private def getLinks(varFields: List[VarField]): List[Relation] =
    SierraParents(createSierraBibDataWith(varFields = varFields))
}
