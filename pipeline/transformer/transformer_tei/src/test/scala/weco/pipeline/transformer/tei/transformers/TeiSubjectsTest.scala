package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.pipeline.transformer.generators.LabelDerivedIdentifiersGenerators
import weco.pipeline.transformer.tei.generators.TeiGenerators

class TeiSubjectsTest
    extends AnyFunSpec
    with TeiGenerators
    with Matchers
    with LabelDerivedIdentifiersGenerators {
  val id = "MS123"
  it("extracts a subject") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(keywords(subjects = List(subject("Botany"))))
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("botany"),
        label = "Botany",
        concepts = List(Concept("Botany"))
      )
    )
  }

  it("extracts a list of subjects") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(subjects =
                List(subject("Botany"), subject("Computers"), subject("Aliens"))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("botany"),
        label = "Botany",
        concepts = List(Concept("Botany"))
      ),
      Subject(
        id = labelDerivedConceptIdentifier("computers"),
        label = "Computers",
        concepts = List(Concept("Computers"))
      ),
      Subject(
        id = labelDerivedConceptIdentifier("aliens"),
        label = "Aliens",
        concepts = List(Concept("Aliens"))
      )
    )
  }
  it("doesn't extract subjects with an empty label") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords = List(keywords(subjects = List(subject("")))))
        )
      )
    )

    result shouldBe Nil
  }
  it("normalises the label of a subject") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords = List(keywords(subjects = List(subject(""" Very
          |   fast
          |
          |   cars  """.stripMargin)))))
        )
      )
    )

    result shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("very fast cars"),
        label = "Very fast cars",
        concepts = List(Concept("Very fast cars"))
      )
    )
  }

  it("extracts a subject with an lcsh id") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#LCSH"),
                subjects = List(subject("Botany", reference = Some("sh12345")))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = Identifiable(
          SourceIdentifier(IdentifierType.LCSubjects, "Concept", "sh12345")
        ),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it("extracts a subject id from attribute key") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#LCSH"),
                subjects = List(<item>
          <term key="sh12345">Botany</term>
        </item>)
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = Identifiable(
          SourceIdentifier(IdentifierType.LCSubjects, "Concept", "sh12345")
        ),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it("prefers to extract from key over ref") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#LCSH"),
                subjects = List(<item>
          <term key="sh12345" ref="#WMS_Arabic_592-item1">Botany</term>
        </item>)
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = Identifiable(
          SourceIdentifier(IdentifierType.LCSubjects, "Concept", "sh12345")
        ),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it("normalises the subject id") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#LCSH"),
                subjects =
                  List(subject("Botany", reference = Some(" sh12345 ")))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = Identifiable(
          SourceIdentifier(IdentifierType.LCSubjects, "Concept", "sh12345")
        ),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it(
    "extracts the correct identifier if the subject id is prepended with subject_sh"
  ) {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#LCSH"),
                subjects = List(
                  subject(
                    "Torah scrolls",
                    reference = Some("subject_sh 85136100")
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = Identifiable(
          SourceIdentifier(IdentifierType.LCSubjects, "Concept", "sh85136100")
        ),
        label = "Torah scrolls",
        concepts = List(Concept(label = "Torah scrolls"))
      )
    )
  }

  it("extracts a MESH subject") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#MeSH"),
                subjects =
                  List(subject("Botany", reference = Some("subject_D001901")))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = Identifiable(
          SourceIdentifier(IdentifierType.MESH, "Concept", "D001901")
        ),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it("extracts the subject without id if it doesn't recognise the authority") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#somethingsomething"),
                subjects =
                  List(subject("Botany", reference = Some("subject_D001901")))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("botany"),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it("extracts a subject with authority no reference") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(
                keywordsScheme = Some("#MeSH"),
                subjects = List(subject("Botany"))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("botany"),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }
  it("extracts subjects from 2 lists with different authorities") {
    val result = TeiSubjects(
      teiXml(
        id = id,
        profileDesc = Some(
          profileDesc(keywords =
            List(
              keywords(subjects =
                List(subject("Botany"), subject("Computers"), subject("Aliens"))
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("botany"),
        label = "Botany",
        concepts = List(Concept("Botany"))
      ),
      Subject(
        id = labelDerivedConceptIdentifier("computers"),
        label = "Computers",
        concepts = List(Concept("Computers"))
      ),
      Subject(
        id = labelDerivedConceptIdentifier("aliens"),
        label = "Aliens",
        concepts = List(Concept("Aliens"))
      )
    )
  }
}
