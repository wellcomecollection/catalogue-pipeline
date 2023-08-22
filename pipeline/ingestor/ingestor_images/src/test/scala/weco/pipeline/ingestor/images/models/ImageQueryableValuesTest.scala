package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState
}
import weco.catalogue.internal_model.image.{InferredData, ParentWork}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{
  Concept,
  Contributor,
  Genre,
  Person,
  Subject,
  WorkData
}

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

    val canonicalWork = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = canonicalData,
      version = 1
    )

    val q = ImageQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      locations = List(),
      inferredData = InferredData.empty,
      source = canonicalWork
    )

    q.source.subjectLabels shouldBe List(
      "Sharp scissors",
      "Split sandwiches",
      "Soft spinners",
      "Straight strings"
    )
  }

  it("adds genres") {
    val canonicalData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      genres = List(
        Genre(label = "Green goblins"),
        Genre(label = "Grand grinches")
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

    val q = ImageQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      locations = List(),
      inferredData = InferredData.empty,
      source = canonicalWork
    )

    q.source.genreLabels shouldBe List(
      "Green goblins",
      "Grand grinches"
    )
  }

  describe("normalising labels") {
    info(
      """ In order to give a consistent experience when querying using labels,
        | label values are normalised when stored for querying and aggregating.
        | The normalisation strips trailing full stops.
        |
        | This normalisation is only to be applied to the queryable and aggregable
        | versions of the label.  The label as displayed on documents should be shown
        | as written by the author of the record.
        |""".stripMargin
    )

    it("normalises subjects and subject concepts") {
      val canonicalData = WorkData[DataState.Identified](
        title = Some(s"title-${randomAlphanumeric(length = 10)}"),
        subjects = List(
          Subject(
            label = "Sharp scissors.",
            concepts = List(Concept("sharpness"), Concept("shearing tools."))
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

      val q = ImageQueryableValues(
        id = createCanonicalId,
        sourceIdentifier = createSourceIdentifier,
        locations = List(),
        inferredData = InferredData.empty,
        source = canonicalWork
      )

      q.source.subjectLabels shouldBe List(
        "Sharp scissors"
      )

      q.source.subjectConceptLabels shouldBe List(
        "sharpness",
        "shearing tools"
      )
    }

    it("normalises genres and genre concepts") {
      val canonicalData = WorkData[DataState.Identified](
        title = Some(s"title-${randomAlphanumeric(length = 10)}"),
        genres = List(
          Genre(
            label = "Green goblins.",
            concepts = List(Concept("greenness"), Concept("goblinity."))
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

      val q = ImageQueryableValues(
        id = createCanonicalId,
        sourceIdentifier = createSourceIdentifier,
        locations = List(),
        inferredData = InferredData.empty,
        source = canonicalWork
      )

      q.source.genreLabels shouldBe List(
        "Green goblins"
      )
      q.source.genreConceptLabels shouldBe List(
        "greenness",
        "goblinity"
      )
    }

    it("normalises contributors") {
      val canonicalData = WorkData[DataState.Identified](
        contributors = List(
          Contributor(
            agent = Person(
              id = IdState.Unidentifiable,
              label = "Joseph Pujol."
            ),
            roles = Nil
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

      val q = ImageQueryableValues(
        id = createCanonicalId,
        sourceIdentifier = createSourceIdentifier,
        locations = List(),
        inferredData = InferredData.empty,
        source = canonicalWork
      )

      q.source.contributorAgentLabels shouldBe List(
        "Joseph Pujol"
      )
    }
  }
}
