package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState
}
import weco.catalogue.internal_model.image.{ParentWork, ParentWorks}
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
    val w = ParentWorks(
      ParentWork(
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
            ),
          ),
          items = List(
            createDigitalItemWith(license = None),
            createDigitalItemWith(license = Some(License.CCBY)),
            createDigitalItemWith(license = Some(License.PDM)),
          )
        ),
        version = 1
      )
    )

    ImageAggregatableValues(w) shouldBe ImageAggregatableValues(
      licenses = List(
        """{"id":"cc-by","label":"Attribution 4.0 International (CC BY 4.0)","url":"http://creativecommons.org/licenses/by/4.0/","type":"License"}""",
        """{"id":"pdm","label":"Public Domain Mark","url":"https://creativecommons.org/share-your-work/public-domain/pdm/","type":"License"}"""
      ),
      contributors = List(
        """{"label":"Polly Person","type":"Person"}""",
        """{"label":"Printer Parsons","type":"Organisation"}""",
        """{"label":"People Professionals","type":"Meeting"}""",
      ),
      genres = List(
        """{"label":"genial giraffes","concepts":[],"type":"Genre"}""",
        """{"label":"gruesome gerunds","concepts":[],"type":"Genre"}"""
      ),
      subjects = List(
        """{"label":"Sharp scissors","concepts":[],"type":"Subject"}""",
        """{"label":"Split sandwiches","concepts":[],"type":"Subject"}""",
        """{"id":"subject1","label":"Soft spinners","concepts":[],"type":"Subject"}""",
        """{"id":"subject2","label":"Straight strings","concepts":[],"type":"Subject"}"""
      )
    )
  }

  it("skips values from the redirected work") {
    val w = ParentWorks(
      canonicalWork = ParentWork(
        id = IdState.Identified(
          canonicalId = createCanonicalId,
          sourceIdentifier = createSourceIdentifier
        ),
        data = WorkData[DataState.Identified](
          title = Some("a work used in the ImageAggregatableValues tests"),
          genres = List(Genre(label = "glum gerbils")),
          subjects = List(Subject(label = "sturdy shapes", concepts = List())),
          contributors = List(
            Contributor(
              id = IdState.Unidentifiable,
              agent = Meeting(label = "proud polyglots"),
              roles = List()
            ),
          ),
          items = List(
            createDigitalItemWith(license = None),
            createDigitalItemWith(license = Some(License.CCBY)),
          )
        ),
        version = 1
      ),
      redirectedWork = Some(
        ParentWork(
          id = IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ),
          data = WorkData[DataState.Identified](
            title = Some("a redirected work used in the ImageAggregatableValues tests"),
            genres = List(Genre(label = "grimy gumballs")),
            subjects = List(Subject(label = "stiff strüdel", concepts = List())),
            contributors = List(
              Contributor(
                id = IdState.Unidentifiable,
                agent = Person(label = "pink panacottas"),
                roles = List()
              ),
            ),
            items = List(
              createDigitalItemWith(license = Some(License.PDM)),
            )
          ),
          version = 1
        )
      )
    )

    ImageAggregatableValues(w) shouldBe ImageAggregatableValues(
      licenses = List(
        """{"id":"cc-by","label":"Attribution 4.0 International (CC BY 4.0)","url":"http://creativecommons.org/licenses/by/4.0/","type":"License"}""",
      ),
      contributors = List(
        """{"label":"proud polyglots","type":"Meeting"}""",
      ),
      genres = List(
        """{"label":"glum gerbils","concepts":[],"type":"Genre"}""",
      ),
      subjects = List(
        """{"label":"sturdy shapes","concepts":[],"type":"Subject"}""",
      )
    )
  }
}
