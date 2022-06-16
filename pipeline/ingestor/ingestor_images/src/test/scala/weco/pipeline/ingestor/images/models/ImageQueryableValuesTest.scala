package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState
}
import weco.catalogue.internal_model.image.{ParentWork, ParentWorks}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{Concept, Genre, Subject, WorkData}

class ImageQueryableValuesTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators {
  it("adds subjects") {
    val canonicalData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      subjects = List(
        Subject(
          label = "Sharp scissors",
          concepts = List(Concept("sharpness"), Concept("shearing tools"))
        ),
        Subject(
          label = "Split sandwiches",
          concepts = List(Concept("split thing"))
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("subject1"),
            sourceIdentifier = createSourceIdentifier
          ),
          label = "Soft spinners",
          concepts = List()
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("subject2"),
            sourceIdentifier = createSourceIdentifier
          ),
          label = "Straight strings",
          concepts = List()
        )
      )
    )

    val redirectedData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      subjects = List(
        Subject(
          label = "Ropey roundels",
          concepts = List(Concept("round things"), Concept("rope-like objects"))
        ),
        Subject(
          label = "Rigid roads",
          concepts = List(Concept("roads, highways, passages"))
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("subject3"),
            sourceIdentifier = createSourceIdentifier
          ),
          label = "Round radishes",
          concepts = List()
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("subject4"),
            sourceIdentifier = createSourceIdentifier
          ),
          label = "Ripe razors",
          concepts = List()
        )
      )
    )

    val canonicalWork = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = canonicalData,
      version = 1
    )

    val redirectedWork = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = redirectedData,
      version = 1
    )

    val q1 = ImageQueryableValues(source = ParentWorks(canonicalWork))

    q1.sourceSubjectLabels shouldBe List(
      "Sharp scissors",
      "Split sandwiches",
      "Soft spinners",
      "Straight strings"
    )

    val q2 =
      ImageQueryableValues(
        source =
          ParentWorks(canonicalWork, redirectedWork = Some(redirectedWork)))

    q2.sourceSubjectLabels shouldBe List(
      "Sharp scissors",
      "Split sandwiches",
      "Soft spinners",
      "Straight strings",
      "Ropey roundels",
      "Rigid roads",
      "Round radishes",
      "Ripe razors"
    )
  }

  it("adds genres") {
    val canonicalData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      genres = List(
        Genre(label = "Green goblins"),
        Genre(label = "Grand grinches"),
      )
    )

    val redirectedData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      genres = List(
        Genre(label = "Gruesome growls"),
        Genre(label = "Grimy grips")
      )
    )

    val canonicalWork = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = canonicalData,
      version = 1
    )

    val redirectedWork = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = redirectedData,
      version = 1
    )

    val q1 = ImageQueryableValues(source = ParentWorks(canonicalWork))

    q1.sourceGenreLabels shouldBe List(
      "Green goblins",
      "Grand grinches",
    )

    val q2 =
      ImageQueryableValues(
        source =
          ParentWorks(canonicalWork, redirectedWork = Some(redirectedWork)))

    q2.sourceGenreLabels shouldBe List(
      "Green goblins",
      "Grand grinches",
      "Gruesome growls",
      "Grimy grips"
    )
  }
}
