package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState
}
import weco.catalogue.internal_model.image.ParentWork
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.generators.ItemsGenerators
import weco.catalogue.internal_model.work.{
  Concept,
  ContributionRole,
  Contributor,
  Genre,
  Meeting,
  Organisation,
  Person,
  Subject,
  WorkData
}

class ImageAggregatableValuesTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with ItemsGenerators {
  it("creates aggregatable values from a source work") {
    val w = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = WorkData[DataState.Identified](
        title = Some("a work used in the ImageAggregatableValues tests"),
        genres = List(
          Genre(label = "genial giraffes"),
          Genre(label = "gruesome gerunds")
        ),
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
        ),
        contributors = List(
          Contributor(
            id = IdState.Unidentifiable,
            agent = Person(label = "Polly Person"),
            roles = List(ContributionRole("playwright"))
          ),
          Contributor(
            id = IdState.Unidentifiable,
            agent = Organisation(label = "Printer Parsons"),
            roles = List(ContributionRole("printer"))
          ),
          Contributor(
            id = IdState.Identified(
              canonicalId = createCanonicalId,
              sourceIdentifier = createSourceIdentifier
            ),
            agent = Meeting(label = "People Professionals"),
            roles = List()
          )
        ),
        items = List(
          createDigitalItemWith(license = None),
          createDigitalItemWith(license = Some(License.CCBY)),
          createDigitalItemWith(license = Some(License.PDM))
        )
      ),
      version = 1
    )

    ImageAggregatableValues(w) shouldBe ImageAggregatableValues(
      licenses = List(
        """{"id":"cc-by","label":"Attribution 4.0 International (CC BY 4.0)","url":"http://creativecommons.org/licenses/by/4.0/","type":"License"}""",
        """{"id":"pdm","label":"Public Domain Mark","url":"https://creativecommons.org/share-your-work/public-domain/pdm/","type":"License"}"""
      ),
      contributors = List(
        """{"label":"Polly Person","type":"Person"}""",
        """{"label":"Printer Parsons","type":"Organisation"}""",
        """{"label":"People Professionals","type":"Meeting"}"""
      ),
      genres = List(
        """{"label":"genial giraffes","concepts":[],"type":"Genre"}""",
        """{"label":"gruesome gerunds","concepts":[],"type":"Genre"}"""
      ),
      subjects = List(
        """{"label":"Sharp scissors","concepts":[],"type":"Subject"}""",
        """{"label":"Split sandwiches","concepts":[],"type":"Subject"}""",
        """{"label":"Soft spinners","concepts":[],"type":"Subject"}""",
        """{"label":"Straight strings","concepts":[],"type":"Subject"}"""
      )
    )
  }

  it("removes identifiers from subjects") {
    val data = WorkData[DataState.Identified](
      title = Some("a work used in the ImageAggregatableValues tests"),
      subjects = List(
        Subject(
          id = IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ),
          label = "impish indicators"
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ),
          label = "ill-fated ideas"
        ),
        Subject(label = "illicit implications", concepts = List())
      )
    )

    val w = ParentWork(
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      ),
      data = data,
      version = 1
    )

    val aggregatableValues = ImageAggregatableValues(w)

    aggregatableValues.subjects shouldBe List(
      """{"label":"impish indicators","concepts":[],"type":"Subject"}""",
      """{"label":"ill-fated ideas","concepts":[],"type":"Subject"}""",
      """{"label":"illicit implications","concepts":[],"type":"Subject"}"""
    )
  }
  describe("normalising labels") {
    info(
      "labels are normalised in aggregations to remove punctuation that is not deliberately contrastive"
    )
    it("normalises subject labels") {
      val data = WorkData[DataState.Identified](
        subjects = List(
          Subject(
            label = "salty sandwiches.",
            concepts = Nil
          )
        )
      )
      val w = ParentWork(
        id = IdState.Identified(
          canonicalId = createCanonicalId,
          sourceIdentifier = createSourceIdentifier
        ),
        data = data,
        version = 1
      )
      ImageAggregatableValues(
        w
      ).subjects shouldBe List(
        """{"label":"salty sandwiches","concepts":[],"type":"Subject"}"""
      )
    }

    it("normalises contributor labels") {
      val data = WorkData[DataState.Identified](
        contributors = List(
          Contributor(
            id = IdState.Unidentifiable,
            agent = Person(label = "Pablo Picasso."),
            roles = List(ContributionRole("painter"))
          )
        )
      )
      val w = ParentWork(
        id = IdState.Identified(
          canonicalId = createCanonicalId,
          sourceIdentifier = createSourceIdentifier
        ),
        data = data,
        version = 1
      )
      ImageAggregatableValues(
        w
      ).contributors shouldBe List(
        """{"label":"Pablo Picasso","type":"Person"}"""
      )
    }
  }
}
