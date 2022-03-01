package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Relation
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraLinksTest extends AnyFunSpec
with Matchers
with SierraDataGenerators  {

  it("returns an empty list if there are no relevant MARC tags") {
    val varFields = List(
      VarField(
        marcTag = Some("999"),
        content = Some("banana")
      )
    )
    getLinks(varFields) shouldBe Nil
  }

  it("returns a Series relation for a 440 - Series Statement/Added Entry-Title field") {
    val varFields = List(
      VarField(
        marcTag = Some("440"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(Relation("A Series"))
  }

  it("returns a Series relation for a 490 - Series Statement field") {
    val varFields = List(
      VarField(
        marcTag = Some("490"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(Relation("A Series"))
  }

  it("returns a Series relation for an 773 - Host Item Entry field") {
    // In phase one, all relations from parent to child are treated as
    // Series links.
    // This is subject to change in a later phase.
    val varFields = List(
      VarField(
        marcTag = Some("773"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(Relation("A Series"))
  }

  it("returns a Series relation for an 830 - Series Added Entry-Uniform Title field") {
    val varFields = List(
      VarField(
        marcTag = Some("830"),
        content = Some("A Series")
      )
    )
    getLinks(varFields) shouldBe List(Relation("A Series"))
  }

  it("returns a list of series relations when multiple relevant MARC fields are present") {
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
        content = Some("A Host")
      ),
      VarField(
        marcTag = Some("830"),
        content = Some("Yet Another Series")
      )
    )
    getLinks(varFields) shouldBe List(
      Relation("A Series"),
      Relation("Another Series"),
      Relation("A Host"),
      Relation("Yet Another Series"),
    )
  }

  it("returns only unique values if there are duplicates") {
    val varFields = List(
      VarField(
        marcTag = Some("440"),
        content = Some("A Series")
      ),
      VarField(
        marcTag = Some("773"),
        content = Some("A Series")
      ),
      VarField(
        marcTag = Some("830"),
        content = Some("Another Series")
      )
    )
    getLinks(varFields) shouldBe List(
      Relation("A Series"),
      Relation("Another Series"),
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
        content = Some(" ; ")
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
      Relation("A Series"),
      Relation("Another Series"),
    )
  }

  it("ignores subfields") {
    // In phase one, the partName is simply to be ignored.
    // This is subject to change in a later phase.
    val varFields = List(
      VarField(
        marcTag = Some("773"),
        subfields = List(Subfield(tag = "w", content = "page 2")),
        content = Some("A Host")
      )
    )
    getLinks(varFields) shouldBe List(Relation("A Host"))
  }

  it("trims known separators between field and subfield") {
    // Expand this test as more of these separators are discovered.
    val varFields = List(
      VarField(
        marcTag = Some("830"),
        subfields = List(Subfield(tag = "v", content = "no. 149.")),
        content = Some("Published papers (Wellcome Chemical Research Laboratories) ;")
      ),
      VarField(
        marcTag = Some("830"),
        subfields = List(Subfield(tag = "v", content = "no. 149.")),
        content = Some("A Series; but with a space after the separator ; ")
      )
    )

    getLinks(varFields) shouldBe List(
      Relation("Published papers (Wellcome Chemical Research Laboratories)"),
      Relation("A Series; but with a space after the separator")
    )
  }

  private def getLinks(varFields: List[VarField]): List[Relation] = {
    SierraParents(createSierraBibDataWith(varFields = varFields))
  }
}
