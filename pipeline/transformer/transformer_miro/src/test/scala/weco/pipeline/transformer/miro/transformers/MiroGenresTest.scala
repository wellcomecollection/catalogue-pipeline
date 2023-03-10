package weco.pipeline.transformer.miro.transformers

import org.scalatest.Assertion
import org.scalatest.Inside.inside
import org.scalatest.Inspectors
import org.scalatest.LoneElement._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Genre, GenreConcept}
import weco.pipeline.transformer.miro.generators.MiroRecordGenerators
import weco.pipeline.transformer.miro.source.MiroRecord

class MiroGenresTest
    extends AnyFunSpec
    with Matchers
    with Inspectors
    with MiroRecordGenerators
    with MiroTransformableWrapper {

  it("has an empty genre list on records without keywords") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecord,
      expectedGenreLabels = List()
    )
  }

  it("uses the image_phys_format field if present") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecordWith(
        physFormat = Some("Painting")
      ),
      expectedGenreLabels = List("Painting")
    )
  }

  it("uses the image_lc_genre field if present") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecordWith(
        lcGenre = Some("Sculpture")
      ),
      expectedGenreLabels = List("Sculpture")
    )
  }

  it("uses the image_phys_format and image_lc_genre fields if both present") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecordWith(
        physFormat = Some("Etching"),
        lcGenre = Some("Woodwork")
      ),
      expectedGenreLabels = List("Etching", "Woodwork")
    )
  }

  it("deduplicates entries in the genre field") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecordWith(
        physFormat = Some("Oil painting"),
        lcGenre = Some("Oil painting")
      ),
      expectedGenreLabels = List("Oil painting")
    )
  }

  it("normalises genre labels and concepts to sentence case") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecordWith(
        physFormat = Some("etching"),
        lcGenre = Some("wood work")
      ),
      expectedGenreLabels = List("Etching", "Wood work")
    )
  }

  it("removes trailing punctuation from genre labels") {
    transformRecordAndCheckGenres(
      miroRecord = createMiroRecordWith(
        physFormat = Some("Printed books.")
      ),
      expectedGenreLabels = List("Printed books")
    )
  }

  private def transformRecordAndCheckGenres(
    miroRecord: MiroRecord,
    expectedGenreLabels: List[String]
  ): Assertion =
    forAll(transformWork(miroRecord).data.genres zip expectedGenreLabels) {
      case (actualGenre, expectedLabel) =>
        actualGenre.label shouldBe expectedLabel
        inside(actualGenre.concepts.loneElement) {
          case GenreConcept(id, label) =>
            label shouldBe expectedLabel
            inside(id.allSourceIdentifiers.loneElement) {
              case SourceIdentifier(identifierType, ontologyType, value) =>
                identifierType shouldBe IdentifierType.LabelDerived
                ontologyType shouldBe "Genre"
                value shouldBe expectedLabel.toLowerCase
            }
        }
    }
}
