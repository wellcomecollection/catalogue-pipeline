package weco.pipeline.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.miro.generators.MiroRecordGenerators

class MiroIdentifiersTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with MiroRecordGenerators {

  it("fixes the malformed INNOPAC ID on L0035411") {
    val miroRecord = createMiroRecordWith(
      innopacID = Some("L 35411 \n\n15551040"),
      imageNumber = "L0035411"
    )

    val otherIdentifiers =
      transformer.getOtherIdentifiers(miroRecord = miroRecord)

    otherIdentifiers shouldBe List(
      createSierraSystemSourceIdentifierWith(
        value = "b15551040"
      )
    )
  }

  it("deduplicates the library references") {
    // This is based on Miro record L0032098
    val miroRecord = createMiroRecordWith(
      libraryRefDepartment = List(Some("EPB"), Some("EPB")),
      libraryRefId = List(Some("20057/B/1"), Some("20057/B/1"))
    )

    val otherIdentifiers =
      transformer.getOtherIdentifiers(miroRecord = miroRecord)

    otherIdentifiers shouldBe List(
      SourceIdentifier(
        identifierType = IdentifierType.MiroLibraryReference,
        ontologyType = "Work",
        value = "EPB 20057/B/1"
      )
    )
  }

  it(
    "uses a distinct type for iconographic numbers, but only if they look like i-numbers"
  ) {
    val miroRecord = createMiroRecordWith(
      libraryRefDepartment =
        List(Some("Iconographic Collection"), Some("Iconographic Collection")),
      libraryRefId = List(Some("590947i"), Some("974.1"))
    )

    val otherIdentifiers =
      transformer.getOtherIdentifiers(miroRecord = miroRecord)

    otherIdentifiers shouldBe List(
      SourceIdentifier(
        identifierType = IdentifierType.IconographicNumber,
        ontologyType = "Work",
        value = "590947i"
      ),
      SourceIdentifier(
        identifierType = IdentifierType.MiroLibraryReference,
        ontologyType = "Work",
        value = "Iconographic Collection 974.1"
      )
    )
  }

  val transformer = new MiroIdentifiers {}
}
