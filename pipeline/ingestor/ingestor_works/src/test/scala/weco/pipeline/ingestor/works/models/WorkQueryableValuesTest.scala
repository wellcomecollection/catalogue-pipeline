package weco.pipeline.ingestor.works.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Concept, Subject, WorkData}
import weco.catalogue.internal_model.work.generators.{
  SubjectGenerators,
  WorkGenerators
}

class WorkQueryableValuesTest extends AnyFunSpec with Matchers with SubjectGenerators with WorkGenerators {
  it("populates subjects correctly") {
    val data = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      subjects = List(
        Subject(
          label = "Silly sausages",
          concepts = List(Concept("silliness"), Concept("cylinders"))
        ),
        Subject(
          label = "Straight scythes",
          concepts = List(Concept("tools"))
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("ssssssss"),
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LCSubjects,
              value = "lcs-soggy",
              ontologyType = "Subject"
            ),
            otherIdentifiers = List(
              SourceIdentifier(
                identifierType = IdentifierType.MESH,
                value = "mesh-soggy",
                ontologyType = "Subject"
              )
            )
          ),
          label = "Soggy sponges",
          concepts = List()
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("SSSSSSSS"),
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LCNames,
              value = "lcs-simon",
              ontologyType = "Subject"
            )
          ),
          label = "Sam Smithington",
          concepts = List()
        )
      )
    )

    val q = WorkQueryableValues(data)

    q.subjectIds shouldBe List("ssssssss", "SSSSSSSS")
    q.subjectIdentifiers shouldBe List("lcs-soggy", "mesh-soggy", "lcs-simon")
    q.subjectLabels shouldBe List("Silly sausages", "Straight scythes", "Soggy sponges", "Sam Smithington")
  }
}
