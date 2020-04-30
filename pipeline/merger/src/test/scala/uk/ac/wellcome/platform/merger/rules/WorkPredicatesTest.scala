package uk.ac.wellcome.platform.merger.rules

import org.scalatest.Inspectors
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{DigitalLocation, PhysicalLocation, TransformedBaseWork}

class WorkPredicatesTest
    extends AnyFunSpec
    with WorksGenerators
    with Matchers
    with Inspectors {
  val works: Seq[TransformedBaseWork] = List(
    createUnidentifiedSierraWork,
    createMiroWork,
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
    createSierraDigitalWork
  )

  it("selects Sierra works") {
    forAll(works.filter(WorkPredicates.sierraWork)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
    }
  }

  it("selects singleDigitalItemMetsWork works") {
    forAll(works.filter(WorkPredicates.singleDigitalItemMetsWork)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "mets"
      work.data.items should have size 1
      work.data.items.head.locations.head shouldBe a[DigitalLocation]
    }
  }

  it("selects singleDigitalItemMiroWork works") {
    forAll(works.filter(WorkPredicates.singleDigitalItemMiroWork)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "miro-image-number"
      work.data.items should have size 1
      work.data.items.head.locations.head shouldBe a[DigitalLocation]
    }
  }

  it("selects single-item Sierra works") {
    forAll(works.filter(WorkPredicates.singleItemSierra)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
      work.data.items should have length 1
    }
  }

  it("selects physical Sierra works") {
    forAll(works.filter(WorkPredicates.physicalSierra)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
      atLeast(1, work.data.items.flatMap(_.locations)) should matchPattern {
        case _: PhysicalLocation =>
      }
    }
  }
}
