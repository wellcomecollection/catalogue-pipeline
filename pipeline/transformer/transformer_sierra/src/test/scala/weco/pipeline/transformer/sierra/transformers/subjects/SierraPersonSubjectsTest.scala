package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdentifierType}
import weco.catalogue.internal_model.work.{Concept, Person, Subject}
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  HasIdMatchers,
  SubjectMatchers
}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.{Subfield, VarField}

class SierraPersonSubjectsTest
    extends AnyFunSpec
    with Matchers
    with HasIdMatchers
    with SubjectMatchers
    with ConceptMatchers
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
    assertCreatesSubjectWithLabel(bibData, label = "A Content")
  }

  it("returns a lowercase ascii normalised identifier") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "600",
          subfields = List(
            Subfield(tag = "a", content = "François")
          )
        )
      )
    )
    val List(subject) = SierraPersonSubjects(bibId, bibData)
    subject.label shouldBe "François"
    subject should have(
      labelDerivedSubjectId("francois")
    )
    subject.onlyConcept.label shouldBe "François"
    subject.onlyConcept should have(
      labelDerivedPersonId("francois")
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

    assertCreatesSubjectWithLabel(bibData, label = "Larrey, D. J. baron")
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
    assertCreatesSubjectWithLabel(
      bibData,
      label = "David Attenborough sir doctor")
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
    assertCreatesSubjectWithLabel(bibData, label = "David Attenborough II")
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

    val List(subject) = SierraPersonSubjects(bibId, bibData)
    subject.label shouldBe "David Attenborough, author"
    subject.onlyConcept shouldBe a[Person[_]]
    subject.onlyConcept.label shouldBe "David Attenborough,"
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
    assertCreatesSubjectWithLabel(
      bibData,
      "Rita Levi Montalcini, 22 April 1909 – 30 December 2012")
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
    val List(subject) = SierraPersonSubjects(bibId, bibData)
    subject.label shouldBe "David Attenborough, author, editor"
    subject.onlyConcept shouldBe a[Person[_]]
    // Not "David Attenborough"
    // See https://github.com/wellcomecollection/catalogue-pipeline/blob/704cec1f6c43496313aebe0cc167e8b5aac32021/pipeline/transformer/transformer_sierra/src/main/scala/weco/pipeline/transformer/sierra/transformers/SierraAgents.scala#L32-L40
    subject.onlyConcept.label shouldBe "David Attenborough,"
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

    val List(subject) = SierraPersonSubjects(bibId, bibData)
    subject should have(
      'label ("Gerry the Garlic"),
      sourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Subject",
        value = lcshCode
      )
    )
    subject.onlyConcept should have(
      'label ("Gerry the Garlic"),
      sourceIdentifier(
        identifierType = IdentifierType.LCNames,
        ontologyType = "Person",
        value = lcshCode
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
      subject.concepts.head shouldBe a[Person[_]]
      all(subject.concepts.tail) shouldBe a[Concept[_]]
      subject.concepts.map(_.label) shouldBe List(
        "Shakespeare, William,",
        "Characters",
        "Hamlet."
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
      subject.onlyConcept shouldBe a[Person[_]]
      subject.onlyConcept should have(
        'label ("Aristophanes. Birds."),
        labelDerivedPersonId("aristophanes. birds")
      )
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
    assertCreatesSubjectWithLabel(
      bibData,
      label =
        "Agate, John, 1676-1720. Sermon preach'd at Exeter, on the 30th of January ...")
  }

  /**
    * Assert that the result of creating subjects with the given bibdata results in a single
    * subject with a single concept, both bearing the given label.
    */
  private def assertCreatesSubjectWithLabel(bibData: SierraBibData,
                                            label: String): Assertion = {
    val List(subject) = SierraPersonSubjects(bibId, bibData)
    subject.label shouldBe label
    subject.onlyConcept shouldBe a[Person[_]]
    subject.onlyConcept.label shouldBe label
  }

}
