package weco.pipeline.ingestor.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.WorkGenerators

import weco.pipeline.ingestor.common.models.WorkQueryableValues

class WorkQueryableValuesTest
    extends AnyFunSpec
    with Matchers
    with IngestorTestData
    with WorkGenerators {

  lazy val testWorkQueryableValues: WorkQueryableValues = WorkQueryableValues(
    canonicalId = testWork.state.canonicalId,
    sourceIdentifier = testWork.sourceIdentifier,
    data = testWork.data,
    relations = testWork.state.relations
  )

  it("transforms works into queryable values") {
    testWorkQueryableValues shouldBe WorkQueryableValues(
      collectionPathLabel = None,
      collectionPathPath = Some("657607i/658296i"),
      alternativeTitles = Nil,
      contributorsAgentLabel =
        List("M.A.C.T", "McGlashan, Alan Fleming, 1898-1997"),
      description =
        Some("<p>Centre, human beings lit up as by a fire in a cave</p>"),
      edition = None,
      genresConceptsLabel = List("Ink drawings", "Drawings"),
      id = "c4zj63fx",
      sourceIdentifierValue = "b16582962",
      identifiersValue = List("b16582962", "1658296", "658296i", "L0045108"),
      imagesId = List("zswkgyan"),
      imagesIdentifiersValue = List("L0045108"),
      itemsId = List("r8mz6j8c"),
      itemsIdentifiersValue = List("i15863207", "1586320"),
      itemsShelfmarksValue = List("EPB/ENCY/1.v1"),
      languagesLabel = List("English"),
      lettering = Some("For Alan ..."),
      notesContents = List(
        "Drawing by a patient of Dr Alan McGlashan",
        "Wellcome Collection 658296i",
        "Possibly related to no. 658132i (summer 1977), which has a similar composition but showing a soft and idyllic scene instead of a harsh and cold one",
        "Related work : M.A.C.T. The dream of a patient in Jungian analysis: a valley, with a meandering path through trees beyond. Drawing by M.A.C.T., 1977. (Wcat)658132i"
      ),
      partOfTitle = List(
        "M.A.C.T.",
        "Dreams of a patient in Jungian analysis. Drawings, 1967-1978."
      ),
      physicalDescription =
        Some("1 drawing : black ink ; sheet 33.8 x 24.7 cm"),
      productionLabel = List("[between 1970 and 1979?]"),
      referenceNumber = Some("658296i"),
      subjectsConceptsLabel = List(
        "Jungian psychology",
        "Dreams",
        "McGlashan, Alan Fleming, 1898-1997"
      ),
      title = Some(
        "The dream of a patient in Jungian analysis: a rocky arch, from which steps lead to a river flowing through a lofty tunnel; left, houses on an embankment; beyond, a fjord. Drawing by M.A.C.T., 197-."
      )
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
      val data = WorkData[DataState.Identified](
        subjects = List(
          Subject(
            label = "Silly sausages.",
            concepts = List(Concept("silliness"), Concept("cylinders."))
          )
        )
      )

      val q = WorkQueryableValues(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier,
        data = data
      )

      q.subjectsConceptsLabel shouldBe List("silliness", "cylinders")
    }
    it("normalises genres and genre concepts") {
      val data = WorkData[DataState.Identified](
        genres = List(
          Genre(
            label = "Green gerbils.",
            concepts = List(Concept("generosity"), Concept("greebles."))
          )
        )
      )

      val q = WorkQueryableValues(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier,
        data = data
      )

      q.genresConceptsLabel shouldBe List("generosity", "greebles")
    }

    it("normalises contributors") {
      val workData = WorkData[DataState.Identified](
        contributors = List(
          Contributor(
            agent = Person(
              id = IdState.Unidentifiable,
              label = "Crafty Carol."
            ),
            roles = Nil
          )
        )
      )

      val q = WorkQueryableValues(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier,
        data = workData
      )

      q.contributorsAgentLabel shouldBe List(
        "Crafty Carol"
      )
    }
  }
}
