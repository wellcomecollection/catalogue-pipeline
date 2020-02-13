package uk.ac.wellcome.platform.merger.rules.sierramiro

import org.scalatest.{Assertion, FunSpec, Matchers}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._

class SierraMiroMergeRuleTest
    extends FunSpec
    with Matchers
    with WorksGenerators
    with PropertyChecks {

  describe("merges multiple Miro works into a Sierra work") {
    val sierraPhysicalWork = createSierraPhysicalWork
    val sierraDigitalWork = createSierraDigitalWork

    it("copies identifiers from all Miro works into the Sierra work") {
      forAll(MiroGenerators.genMiroWorks) { miroWorks: List[UnidentifiedWork] =>
        val (mergedWork, _) =
          getMergedAndRedirectedWorks(miroWorks :+ sierraPhysicalWork)
        mergedWork.sourceIdentifier shouldBe sierraPhysicalWork.sourceIdentifier
        mergedWork.otherIdentifiers shouldBe
          sierraPhysicalWork.otherIdentifiers ++ miroWorks.flatMap(
            _.identifiers)
      }
    }

    it("redirects all Miro works to the Sierra work") {
      forAll(MiroGenerators.genMiroWorks) { miroWorks: List[UnidentifiedWork] =>
        val (_, redirectedWorks) =
          getMergedAndRedirectedWorks(miroWorks :+ sierraPhysicalWork)
        every(redirectedWorks) should matchPattern {
          case UnidentifiedRedirectedWork(
              _,
              _,
              IdentifiableRedirect(sierraPhysicalWork.sourceIdentifier),
              _) =>
        }
      }
    }

    it("copies item locations from all Miro works to a physical Sierra work") {
      forAll(MiroGenerators.genMiroWorks) { miroWorks: List[UnidentifiedWork] =>
        val (mergedWork, _) =
          getMergedAndRedirectedWorks(miroWorks :+ sierraPhysicalWork)
        mergedWork.data.items shouldBe List(
          sierraPhysicalWork.data.items.head.copy(
            locations = sierraPhysicalWork.data.items.head.locations ++
              miroWorks.flatMap(_.data.items.head.locations)
          )
        )
      }
    }

    it("copies Miro locations if the Sierra work already has a DigitalLocation") {
      forAll(MiroGenerators.genMiroWorks) { miroWorks: List[UnidentifiedWork] =>
        val (mergedDigitalWork, _) =
          getMergedAndRedirectedWorks(miroWorks :+ sierraDigitalWork)
        mergedDigitalWork.data.items shouldBe List(
          sierraDigitalWork.data.items.head.copy(
            locations = sierraDigitalWork.data.items.head.locations ++
              miroWorks.flatMap(_.data.items.head.locations)
          )
        )
      }
    }

    it("uses the work with the first Miro ID for the thumbnail") {
      forAll(MiroGenerators.genMiroWorks) { miroWorks: List[UnidentifiedWork] =>
        val (mergedWork, _) =
          getMergedAndRedirectedWorks(miroWorks :+ sierraPhysicalWork)
        val firstId = miroWorks.map(_.sourceIdentifier.value).min
        val miroWorkWithFirstId =
          miroWorks.find(_.sourceIdentifier.value == firstId).get
        mergedWork.data.thumbnail shouldBe miroWorkWithFirstId.data.thumbnail
      }
    }
  }

  def getMergedAndRedirectedWorks(works: Seq[BaseWork])
    : (UnidentifiedWork, Seq[UnidentifiedRedirectedWork]) = {
    val result = mergeAndRedirectWorks(works)

    result should have size works.size
    result.head shouldBe an[UnidentifiedWork]
    every(result.tail) shouldBe an[UnidentifiedRedirectedWork]

    (
      result.head.asInstanceOf[UnidentifiedWork],
      result.tail.map(_.asInstanceOf[UnidentifiedRedirectedWork])
    )
  }

  describe(
    "does not merge unless passed exactly one Sierra work and any number of Miro works") {
    it("does not merge a single Sierra  work") {
      assertWorksAreNotMerged(createUnidentifiedSierraWork)
    }

    it("does not merge a single Sierra physical work") {
      assertWorksAreNotMerged(createMiroWork)
    }

    it("does not merge multiple Sierra works") {
      assertWorksAreNotMerged(
        createUnidentifiedSierraWork,
        createUnidentifiedSierraWork)
    }

    it("does not merge multiple Miro works") {
      assertWorksAreNotMerged(createMiroWork, createMiroWork)
    }

    it("does not merge if there are no Sierra works") {
      assertWorksAreNotMerged(createIsbnWorks(5): _*)
    }

    it(
      "does not merge if there are multiple Sierra works with a single Miro work") {
      assertWorksAreNotMerged(
        createMiroWork,
        createUnidentifiedSierraWork,
        createUnidentifiedSierraWork)
    }
  }

  private def assertWorksAreNotMerged(works: BaseWork*): Assertion =
    mergeAndRedirectWorks(works) shouldBe works

  private def mergeAndRedirectWorks(works: Seq[BaseWork]): Seq[BaseWork] = {
    val result = SierraMiroMergeRule.mergeAndRedirectWorks(works)

    // We always get the same number of works as we started with.
    result.size shouldBe works.size

    // Stronger: the source identifiers are preserved on the way through.
    result.map { _.sourceIdentifier } should contain theSameElementsAs works
      .map { _.sourceIdentifier }

    result
  }
}

object MiroGenerators extends WorksGenerators with Matchers {
  final private val MIRO_PREFIXES =
    List("C", "L", "V", "W", "N", "M", "B", "A", "S", "F")

  val genMiroId: Gen[String] =
    for {
      prefix <- Gen.oneOf(MIRO_PREFIXES)
      n <- Gen.choose(1, 9999999)
    } yield f"$prefix%s$n%07d"

  val genMiroIds: Gen[List[String]] = Gen.nonEmptyListOf(genMiroId)

  val genMiroWorks: Gen[List[UnidentifiedWork]] =
    genMiroIds.map {
      _.map { id =>
        createUnidentifiedWorkWith(
          sourceIdentifier = SourceIdentifier(
            identifierType = IdentifierType("miro-image-number"),
            ontologyType = "Work",
            value = id
          ),
          items = List(
            createUnidentifiableItemWith(
              locations = List(
                createDigitalLocationWith(
                  locationType = createImageLocationType)
              )
            )
          ),
          thumbnail = Some(
            DigitalLocation(
              url = s"https://iiif.wellcomecollection.org/$id.jpg",
              locationType = LocationType("thumbnail-image"),
              license = Some(License.CCBY)
            )
          )
        )
      }
    }
}
