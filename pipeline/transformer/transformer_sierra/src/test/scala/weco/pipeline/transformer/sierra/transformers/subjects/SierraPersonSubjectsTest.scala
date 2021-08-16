package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Concept, Person, Subject}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraPersonSubjectsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  def bibId = createSierraBibNumber

  it("returns zero subjects if there are none") {
    val bibData = createSierraBibDataWith(varFields = List())
    SierraPersonSubjects(bibId, bibData) shouldBe Nil
  }

  it("returns subjects for tag 600 with only subfield a") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content",
        concepts = List(Person(label = "A Content"))
      )
    )
  }

  it("returns subjects for tag 600 with only subfields a and c") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "Larrey, D. J."),
            Subfield(tag = "c", content = "baron")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "Larrey, D. J. baron",
        concepts = List(
          Person(label = "Larrey, D. J. baron")
        )
      )
    )
  }

  it("returns subjects for tag 600 with only subfields a and multiple c") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "David Attenborough"),
            Subfield(tag = "c", content = "sir"),
            Subfield(tag = "c", content = "doctor")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "David Attenborough sir doctor",
        concepts = List(Person(label = "David Attenborough sir doctor"))
      )
    )
  }

  it("returns subjects for tag 600 with only subfields a and b") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "David Attenborough"),
            Subfield(tag = "b", content = "II")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "David Attenborough II",
        concepts = List(Person(label = "David Attenborough II"))
      )
    )
  }

  it("returns subjects for tag 600 with subfields a and e") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "David Attenborough,"),
            Subfield(tag = "e", content = "author")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "David Attenborough, author",
        concepts = List(Person(label = "David Attenborough,"))
      )
    )
  }

  it("returns subjects for tag 600 with subfields a and d") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "Rita Levi Montalcini,"),
            Subfield(tag = "d", content = "22 April 1909 – 30 December 2012")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "Rita Levi Montalcini, 22 April 1909 – 30 December 2012",
        concepts = List(
          Person(
            label = "Rita Levi Montalcini, 22 April 1909 – 30 December 2012"))
      )
    )
  }

  it("returns subjects for tag 600 with subfields a and multiple e") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "David Attenborough,"),
            Subfield(tag = "e", content = "author,"),
            Subfield(tag = "e", content = "editor")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "David Attenborough, author, editor",
        concepts = List(Person(label = "David Attenborough,"))
      )
    )
  }

  // There's nothing useful we can do here.  Arguably it's a cataloguing
  // error, but all the person will do is delete the field, so we can avoid
  // throwing an error.
  it("errors transforming a subject 600 if subfield a is missing") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List()
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe Nil
  }

  it(
    "creates an identifiable subject with library of congress heading if there is a subfield 0 and the second indicator is 0") {
    val name = "Gerry the Garlic"
    val lcshCode = "lcsh7212"

    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "600",
          indicator2 = "0",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = lcshCode)
          )
        )
      )
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LCNames,
      ontologyType = "Subject",
      value = lcshCode
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        id = IdState.Identifiable(sourceIdentifier),
        label = "Gerry the Garlic",
        concepts = List(Person(label = "Gerry the Garlic"))
      )
    )
  }

  it("creates an unidentifiable person concept if second indicator is not 0") {
    val name = "Gerry the Garlic"
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "600",
          indicator2 = "2",
          subfields = List(
            Subfield(tag = "a", content = name),
            Subfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "Gerry the Garlic",
        concepts = List(Person(label = "Gerry the Garlic"))
      )
    )
  }

  describe("includes the contents of subfield x") {
    // Based on https://search.wellcomelibrary.org/iii/encore/record/C__Rb1159639?marcData=Y
    // as retrieved 22 January 2019.
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "Shakespeare, William,"),
            Subfield(tag = "x", content = "Characters"),
            Subfield(tag = "x", content = "Hamlet.")
          )
        )
      )
    )

    val actualSubjects = SierraPersonSubjects(bibId, bibData)
    actualSubjects should have size 1
    val subject = actualSubjects.head

    it("in the concepts") {
      subject.concepts shouldBe List(
        Person("Shakespeare, William,"),
        Concept("Characters"),
        Concept("Hamlet.")
      )
    }

    it("in the label") {
      subject.label shouldBe "Shakespeare, William, Characters Hamlet."
    }
  }

  describe("includes the contents of subfield t") {
    // Based on https://search.wellcomelibrary.org/iii/encore/record/C__Rb1190271?marcData=Y
    // as retrieved 22 January 2019.
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "Aristophanes."),
            Subfield(tag = "t", content = "Birds.")
          )
        )
      )
    )

    val actualSubjects = SierraPersonSubjects(bibId, bibData)
    actualSubjects should have size 1
    val subject = actualSubjects.head

    it("in the concepts") {
      subject.concepts shouldBe List(Person("Aristophanes. Birds."))
    }

    it("in the label") {
      subject.label shouldBe "Aristophanes. Birds."
    }
  }

  it("doesn't remove a trailing ellipsis from a subject label") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "Agate, John,"),
            Subfield(tag = "d", content = "1676-1720."),
            Subfield(
              tag = "t",
              content = "Sermon preach'd at Exeter, on the 30th of January ...")
          )
        )
      )
    )

    SierraPersonSubjects(bibId, bibData) shouldBe List(
      Subject(
        "Agate, John, 1676-1720. Sermon preach'd at Exeter, on the 30th of January ...",
        concepts = List(Person(
          "Agate, John, 1676-1720. Sermon preach'd at Exeter, on the 30th of January ..."))
      ))
  }
}
