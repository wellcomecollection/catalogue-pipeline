package weco.pipeline.transformer.marc_common.transformers
import org.scalatest.LoneElement
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{
  Concept,
  Meeting,
  Organisation,
  Period,
  Person,
  Place
}
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcSubjectsTest
    extends MarcAbstractAgentBehaviours
    with LoneElement
    with TableDrivenPropertyChecks {
  describe("When there are no subjects") {
    it("returns nothing if no relevant fields are present") {
      MarcSubjects(
        MarcTestRecord(fields =
          Seq(
            MarcField(
              marcTag = "245",
              subfields = Seq(MarcSubfield(tag = "a", content = "The Title"))
            )
          )
        )
      ) shouldBe Nil
    }

    it("returns nothing if the only relevant fields are invalid") {
      MarcSubjects(
        MarcTestRecord(fields =
          Seq(
            MarcField(
              marcTag = "610",
              subfields = Nil
            ),
            MarcField(
              marcTag = "600",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "   "),
                MarcSubfield(tag = "b", content = ""),
                MarcSubfield(tag = "c", content = "  ")
              )
            )
          )
        )
      ) shouldBe Nil
    }

  }
  describe("extracting subjects from various fields") {
    forAll(
      Table(
        ("marcTag", "subjectType"),
        ("600", a[Person[IdState.Identifiable]]),
        ("610", a[Organisation[IdState.Identifiable]]),
        ("611", a[Meeting[IdState.Identifiable]]),
        ("648", a[Period[IdState.Identifiable]]),
        ("650", a[Concept[IdState.Identifiable]]),
        ("651", a[Place[IdState.Identifiable]])
      )
    ) {
      (marcTag, subjectType) =>
        it(
          s"creates a Subject containing a ${subjectType.clazzTag.toString().split('.').last} given a $$$marcTag field"
        ) {
          val subject = MarcSubjects(
            MarcTestRecord(fields =
              Seq(
                MarcField(
                  marcTag = marcTag,
                  subfields =
                    Seq(MarcSubfield(tag = "a", content = "The Title"))
                )
              )
            )
          ).loneElement
          subject.concepts.loneElement shouldBe subjectType
        }
    }

    it("extracts all subjects in document order") {
      // An assortment of subject-generating tags, containing duplicates, in no particular order
      val marcTags = Seq(
        "610",
        "611",
        "650",
        "648",
        "600",
        "651",
        "600",
        "610",
        "611",
        "648"
      )
      val subjects = MarcSubjects(
        MarcTestRecord(fields =
          marcTags.map(
            marcTag =>
              MarcField(
                marcTag = marcTag,
                subfields = Seq(MarcSubfield(tag = "a", content = s"$marcTag"))
              )
          )
        )
      )

      subjects.map(_.label) should contain theSameElementsAs marcTags
    }
  }

  describe("extracting Organisation Subjects") {
    val subject = MarcSubjects(
      MarcTestRecord(fields =
        Seq(
          MarcField(
            marcTag = "610",
            subfields = Seq(
              MarcSubfield(tag = "a", content = "A"),
              MarcSubfield(tag = "b", content = "B"),
              MarcSubfield(tag = "c", content = "C"),
              MarcSubfield(tag = "d", content = "D"),
              MarcSubfield(tag = "e", content = "E"),
              MarcSubfield(tag = "t", content = "T"),
              MarcSubfield(tag = "p", content = "P"),
              MarcSubfield(tag = "q", content = "Q"),
              MarcSubfield(tag = "l", content = "L")
            )
          )
        )
      )
    ).loneElement

    it("builds the subject label from subfields a,b,c,d,e") {
      subject.label shouldBe "A B C D E"
    }

    it("builds the organisation label from subfields a,b") {
      subject.concepts.loneElement.label shouldBe "A B"
    }
  }
}
