package weco.pipeline.ingestor.works.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations.{License, LocationType}
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  PeriodGenerators,
  ProductionEventGenerators
}
import weco.catalogue.internal_model.work._

class WorkAggregatableValuesTest
    extends AnyFunSpec
    with Matchers
    with ItemsGenerators
    with ProductionEventGenerators
    with PeriodGenerators {
  it("creates aggregatable values") {
    val data = WorkData[DataState.Identified](
      title = Some("a work used in the ImageAggregatableValues tests"),
      format = Some(Format.CDRoms),
      genres = List(
        Genre(label = "grabby gerbils", concepts = List(Concept("rodents"))),
        Genre(label = "grouchy groundhogs")
      ),
      subjects = List(
        Subject(
          label = "salty sandwiches",
          concepts = List(Concept("taste"), Concept("foodstuffs"))),
        Subject(
          label = "silly sausages",
          concepts = List(Concept("foodstuffs"))),
        Subject(label = "secret spies", concepts = List(Concept("espionage"))),
      ),
      contributors = List(
        Contributor(
          id = IdState.Unidentifiable,
          agent = Person(label = "Pablo Picasso"),
          roles = List(ContributionRole("painter"))
        ),
        Contributor(
          id = IdState.Unidentifiable,
          agent = Organisation(label = "Peacekeeper Percy"),
          roles = List(ContributionRole("peacebody"))
        ),
        Contributor(
          id = IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ),
          agent = Meeting(label = "Pocket Pianos"),
          roles = List()
        ),
      ),
      items = List(
        createDigitalItemWith(license = None),
        createDigitalItemWith(license = Some(License.PDM)),
        createDigitalItemWith(license = Some(License.CCBY)),
        createIdentifiedItemWith(
          locations = List(
            createPhysicalLocationWith(
              locationType = LocationType.ClosedStores,
              license = None)))
      ),
      languages = List(
        Language(id = "eng", label = "English"),
        Language(id = "fre", label = "French")
      ),
      production = List(
        createProductionEventWith(
          dates = List(
            createPeriodForYearRange(startYear = "2000", endYear = "2000"),
            createPeriodForYearRange(startYear = "2010", endYear = "2020")
          )
        ),
        createProductionEventWith(
          dates = List(
            createPeriodForYearRange(startYear = "1900", endYear = "1950"),
          )
        ),
        createProductionEventWith(dates = List())
      )
    )

    val availabilities: Set[Availability] = Set(
      Availability.Online,
      Availability.ClosedStores,
    )

    WorkAggregatableValues(data, availabilities) shouldBe WorkAggregatableValues(
      workTypes = List(
        """{"id":"m","label":"CD-Roms","type":"Format"}"""
      ),
      genres = List(
        """{"label":"grabby gerbils","concepts":[],"type":"Genre"}""",
        """{"label":"grouchy groundhogs","concepts":[],"type":"Genre"}"""
      ),
      productionDates = List(
        """{"label":"2000","type":"Period"}""",
        """{"label":"2010","type":"Period"}""",
        """{"label":"1900","type":"Period"}""",
      ),
      subjects = List(
        """{"label":"salty sandwiches","concepts":[],"type":"Subject"}""",
        """{"label":"silly sausages","concepts":[],"type":"Subject"}""",
        """{"label":"secret spies","concepts":[],"type":"Subject"}"""
      ),
      languages = List(
        """{"id":"eng","label":"English","type":"Language"}""",
        """{"id":"fre","label":"French","type":"Language"}"""
      ),
      contributors = List(
        """{"label":"Pablo Picasso","type":"Person"}""",
        """{"label":"Peacekeeper Percy","type":"Organisation"}""",
        """{"label":"Pocket Pianos","type":"Meeting"}""",
      ),
      itemLicenses = List(
        """{"id":"pdm","label":"Public Domain Mark","url":"https://creativecommons.org/share-your-work/public-domain/pdm/","type":"License"}""",
        """{"id":"cc-by","label":"Attribution 4.0 International (CC BY 4.0)","url":"http://creativecommons.org/licenses/by/4.0/","type":"License"}"""
      ),
      availabilities = List(
        """{"id":"closed-stores","label":"Closed stores","type":"Availability"}""",
        """{"id":"online","label":"Online","type":"Availability"}"""
      ),
    )
  }
}
