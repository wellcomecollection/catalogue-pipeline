package uk.ac.wellcome.platform.merger.rules

import org.scalatest.{FunSpec, Inspectors, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  PhysicalLocation,
  TransformedBaseWork,
}

class WorkPredicatesTest
    extends FunSpec
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

  it("selects METS works") {
    forAll(works.filter(WorkPredicates.metsWork)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "mets"
    }
  }

  it("selects Miro works") {
    forAll(works.filter(WorkPredicates.miroWork)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "miro-image-number"
    }
  }

  it("selects single-item digital METS works") {
    forAll(works.filter(WorkPredicates.singleItemDigitalMets)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "mets"
      work.data.items should have length 1
      every(work.data.items.head.locations) should matchPattern {
        case _: DigitalLocation =>
      }
    }
  }

  it("selects single-item Miro works") {
    forAll(works.filter(WorkPredicates.singleItemMiro)) { work =>
      work.sourceIdentifier.identifierType.id shouldBe "miro-image-number"
      work.data.items should have length 1
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

//  it("selects digital Sierra works") {
//    forAll(works.filter(WorkPredicates.digitalSierra)) { work =>
//      work.sourceIdentifier.identifierType.id shouldBe "sierra-system-number"
//      every(work.data.items.flatMap(_.locations)) should matchPattern {
//        case _: DigitalLocation =>
//      }
//    }
//  }
}
