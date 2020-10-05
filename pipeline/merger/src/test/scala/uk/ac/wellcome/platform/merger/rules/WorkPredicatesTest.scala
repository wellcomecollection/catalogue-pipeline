package uk.ac.wellcome.platform.merger.rules

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.{MetsWorkGenerators, MiroWorkGenerators}
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.generators.WorksWithImagesGenerators

class WorkPredicatesTest
    extends AnyFunSpec
    with WorksWithImagesGenerators
    with MetsWorkGenerators
    with MiroWorkGenerators
    with Matchers
    with Inspectors {
  val works: Seq[Work[Source]] = List(
    sierraSourceWork(),
    createMiroWorkWith(
      sourceIdentifier = createNonHistoricalLibraryMiroSourceIdentifier,
      images = List(createUnmergedMiroImage)
    ),
    createMiroWorkWith(
      sourceIdentifier = createHistoricalLibraryMiroSourceIdentifier,
      images = List(createUnmergedMiroImage)
    ),
    metsSourceWork().invisible(),
    metsSourceWork()
      .items((0 to 3).map { _ => createDigitalItem }.toList)
      .images(List(createUnmergedMetsImage))
      .invisible(),
    sourceWork(sourceIdentifier = createMiroSourceIdentifier)
      .otherIdentifiers(List.empty)
      .thumbnail(miroThumbnail())
      .items(miroItems(count = 3)),
    sierraSourceWork()
      .items(
        (0 to 3).map(_ => createUnidentifiableItemWith(locations = List(createDigitalLocation))).toList
      ),
    sierraPhysicalSourceWork(),
    sierraDigitalSourceWork(),
    sierraSourceWork().format(Format.`3DObjects`),
    sierraSourceWork().format(Format.DigitalImages),
    sierraSourceWork().format(Format.Pictures),
    sierraSourceWork().format(Format.Music),
  )

  it("selects Sierra works") {
    val filtered = works.filter(WorkPredicates.sierraWork)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
    }
  }

  it("selects singleDigitalItemMetsWork works") {
    val filtered = works.filter(WorkPredicates.singleDigitalItemMetsWork)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "mets"
      work.data.items should have size 1
      work.data.items.head.locations.head shouldBe a[DigitalLocationDeprecated]
    }
  }

  it("selects singleDigitalItemMiroWork works") {
    val filtered = works.filter(WorkPredicates.singleDigitalItemMiroWork)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "miro-image-number"
      work.data.items should have size 1
      work.data.items.head.locations.head shouldBe a[DigitalLocationDeprecated]
    }
  }

  it("selects single-item Sierra works") {
    val filtered = works.filter(WorkPredicates.singleItemSierra)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
      work.data.items should have length 1
    }
  }

  it("selects physical Sierra works") {
    val filtered = works.filter(WorkPredicates.physicalSierra)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
      atLeast(1, work.data.items.flatMap(_.locations)) should matchPattern {
        case _: PhysicalLocationDeprecated =>
      }
    }
  }

  it("selects historical library Miro works") {
    val filtered = works.filter(WorkPredicates.historicalLibraryMiro)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.value should startWith regex "[LM]"
    }
  }

  it("selects Picture/Digital Image/3D Object Sierra works") {
    val filtered =
      works.filter(WorkPredicates.sierraPictureDigitalImageOr3DObject)
    filtered should not be empty
    forAll(filtered) { work =>
      work.data.format should contain oneOf
        (Format.Pictures,
        Format.DigitalImages,
        Format.`3DObjects`)
    }
  }
}
