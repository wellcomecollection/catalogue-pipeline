package weco.pipeline.transformer.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.Inspectors
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  Contributor,
  Organisation,
  Place,
  Subject
}

class SubjectsAndContributorsTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks
    with Inspectors {

  it("replaces the type of a Contributor Agent if it matches a Subject") {
    val glasgowAsAnOrg = Contributor(
      agent = Organisation(
        Identifiable(
          SourceIdentifier(
            IdentifierType.LCSubjects,
            "Organisation",
            "sh00000000"
          )
        ),
        "Glasgow"
      ),
      roles = Nil
    )
    val glasgowAsAPlace = new Subject(
      label = "Glasgow",
      id = Identifiable(
        SourceIdentifier(
          IdentifierType.LCSubjects,
          "Place",
          "sh00000000"
        )
      )
    )

    val (subjects, contributors) =
      SubjectsAndContributors(List(glasgowAsAPlace), List(glasgowAsAnOrg))
    // They all had the same label before, so they are no longer unique having harmonised their types
    subjects.length shouldBe 1

    subjects.head shouldBe glasgowAsAPlace
    contributors.head.agent
      .asInstanceOf[Place[IdState.Identifiable]]
      .id
      .sourceIdentifier
      .ontologyType shouldBe "Place"
  }
}
