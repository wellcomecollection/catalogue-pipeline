package uk.ac.wellcome.platform.merger.rules

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  PhysicalLocation,
  TransformedBaseWork,
  WorkType
}

class WorkPredicatesTest
    extends AnyFunSpec
    with WorksGenerators
    with Matchers
    with Inspectors {
  val works: Seq[TransformedBaseWork] = List(
    createUnidentifiedSierraWork,
    createMiroWorkWith(
      sourceIdentifier = createNonHistoricalLibraryMiroSourceIdentifier),
    createMiroWorkWith(
      sourceIdentifier = createHistoricalLibraryMiroSourceIdentifier),
    createUnidentifiedInvisibleMetsWork,
    createUnidentifiedInvisibleMetsWorkWith(
      sourceIdentifier = createMetsSourceIdentifier,
      items = (0 to 3).map(_ => createDigitalItem).toList
    ),
    createUnidentifiedWorkWith(
      sourceIdentifier = createMiroSourceIdentifier,
      otherIdentifiers = List(),
      thumbnail = createMiroWork.data.thumbnail,
      items = (0 to 3).flatMap(_ => createMiroWork.data.items).toList
    ),
    createSierraDigitalWorkWith(
      items = (0 to 3).flatMap(_ => createSierraDigitalWork.data.items).toList
    ),
    createSierraPhysicalWork,
    createSierraDigitalWork,
    createUnidentifiedSierraWorkWith(
      workType = Some(WorkType.`3DObjects`)
    ),
    createUnidentifiedSierraWorkWith(
      workType = Some(WorkType.DigitalImages)
    ),
    createUnidentifiedSierraWorkWith(
      workType = Some(WorkType.Pictures)
    ),
    createUnidentifiedSierraWorkWith(
      workType = Some(WorkType.Music)
    )
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
      work.data.items.head.locations.head shouldBe a[DigitalLocation]
    }
  }

  it("selects singleDigitalItemMiroWork works") {
    val filtered = works.filter(WorkPredicates.singleDigitalItemMiroWork)
    filtered should not be empty
    forAll(filtered) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "miro-image-number"
      work.data.items should have size 1
      work.data.items.head.locations.head shouldBe a[DigitalLocation]
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
        case _: PhysicalLocation =>
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
      work.data.workType should contain oneOf
        (WorkType.Pictures,
        WorkType.DigitalImages,
        WorkType.`3DObjects`)
    }
  }
}
