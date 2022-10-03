package weco.pipeline.ingestor.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.{ContributorGenerators, ItemsGenerators, WorkGenerators}
import weco.pipeline.ingestor.common.models.WorkQueryableValues

import java.time.LocalDate

class WorkQueryableValuesTest
    extends AnyFunSpec
    with Matchers
    with ContributorGenerators
    with ItemsGenerators
    with ImageGenerators
    with WorkGenerators {
  it("adds basic work info") {
    val id = createCanonicalId
    val format = Format.Books
    val workType = WorkType.Series

    val workData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      format = Some(format),
      workType = workType
    )

    val q = WorkQueryableValues(
      id,
      sourceIdentifier = createSourceIdentifier,
      workData,
      relations = Relations(),
      availabilities = Set()
    )

    q.formatId shouldBe Some("a")
    q.workType shouldBe "Series"
  }

  it("sets identifiers") {
    val id = CanonicalId("iiiiiiii")
    val sourceIdentifier = createSourceIdentifierWith(value = "b12345678")
    val otherIdentifiers =
      List("V0000001", "PP/CRI/1", "lcsh-fish").map(value =>
        createSourceIdentifierWith(value = value))
    val workData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      otherIdentifiers = otherIdentifiers
    )

    val q = WorkQueryableValues(
      id,
      sourceIdentifier,
      workData,
      relations = Relations(),
      availabilities = Set()
    )

    q.id shouldBe "iiiiiiii"
    q.workIdentifiers shouldBe List(
      "b12345678",
      "V0000001",
      "PP/CRI/1",
      "lcsh-fish")
  }

  it("adds subjects") {
    val data = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      subjects = List(
        Subject(
          label = "Silly sausages",
          concepts = List(Concept("silliness"), Concept("cylinders"))
        ),
        Subject(
          label = "Straight scythes",
          concepts = List(Concept("tools"))
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("ssssssss"),
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LCSubjects,
              value = "lcs-soggy",
              ontologyType = "Subject"
            ),
            otherIdentifiers = List(
              SourceIdentifier(
                identifierType = IdentifierType.MESH,
                value = "mesh-soggy",
                ontologyType = "Subject"
              )
            )
          ),
          label = "Soggy sponges",
          concepts = List()
        ),
        Subject(
          id = IdState.Identified(
            canonicalId = CanonicalId("SSSSSSSS"),
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LCNames,
              value = "lcs-simon",
              ontologyType = "Subject"
            )
          ),
          label = "Sam Smithington",
          concepts = List()
        )
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = data,
      relations = Relations(),
      availabilities = Set()
    )

    q.subjectIds shouldBe List("ssssssss", "SSSSSSSS")
    q.subjectLabels shouldBe List(
      "Silly sausages",
      "Straight scythes",
      "Soggy sponges",
      "Sam Smithington")
    q.subjectConceptLabels shouldBe List("silliness", "cylinders", "tools")
  }

  it("adds genres") {
    val data = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      genres = List(
        Genre(
          label = "Green gerbils",
          concepts = List(Concept("generosity"), Concept("greebles"))
        ),
        Genre(
          label = "Grim giants",
          concepts = List(Concept("greatness"))
        ),
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = data,
      relations = Relations(),
      availabilities = Set()
    )

    q.genreLabels shouldBe List("Green gerbils", "Grim giants")
    q.genreConceptLabels shouldBe List("generosity", "greebles", "greatness")
  }

  it("adds languages") {
    val workData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      languages = List(
        Language(id = "eng", label = "English"),
        Language(id = "ger", label = "German"),
        Language(id = "fre", label = "French"),
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = workData,
      relations = Relations(),
      availabilities = Set()
    )

    q.languageIds shouldBe List("eng", "ger", "fre")
  }

  it("adds contributors") {
    val workData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      contributors = List(
        Contributor(
          agent = Person(
            id = IdState.Unidentifiable,
            label = "Crafty Carol"
          ),
          roles = List(),
        ),
        Contributor(
          agent = Person(
            id = IdState.Identified(
              canonicalId = CanonicalId("craftyci"),
              sourceIdentifier = createSourceIdentifier,
            ),
            label = "Cruel Cinderella"
          ),
          roles = List(),
        ),
        Contributor(
          agent = Person(
            id = IdState.Identified(
              canonicalId = CanonicalId("carefulc"),
              sourceIdentifier = createSourceIdentifier,
            ),
            label = "Careful Carlos"
          ),
          roles = List(),
        ),
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = workData,
      relations = Relations(),
      availabilities = Set()
    )

    q.contributorAgentIds shouldBe List("craftyci", "carefulc")
    q.contributorAgentLabels shouldBe List(
      "Crafty Carol",
      "Cruel Cinderella",
      "Careful Carlos")
  }

  it("adds items") {
    val data = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      items = List(
        createUnidentifiableItemWith(locations = List()),
        createIdentifiedItemWith(
          canonicalId = CanonicalId("item1111"),
          sourceIdentifier = createSourceIdentifierWith(value = "sourceItem1"),
          otherIdentifiers = List(),
          locations = List(
            createPhysicalLocationWith(
              locationType = LocationType.OpenShelves,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.OpenShelves,
                  status = AccessStatus.Open
                ),
                AccessCondition(
                  method = AccessMethod.OpenShelves,
                  status = AccessStatus.OpenWithAdvisory
                )
              ),
              license = None
            ),
            createPhysicalLocationWith(
              locationType = LocationType.ClosedStores,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.NotRequestable,
                  status = AccessStatus.Closed
                )
              ),
              license = None
            )
          )
        ),
        createIdentifiedItemWith(
          canonicalId = CanonicalId("item2222"),
          sourceIdentifier = createSourceIdentifierWith(value = "sourceItem2"),
          otherIdentifiers = List(
            createSourceIdentifierWith(value = "otherItem2")
          ),
          locations = List(
            createDigitalLocationWith(
              locationType = LocationType.IIIFImageAPI,
              license = Some(License.CCBY),
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.OpenWithAdvisory
                )
              )
            ),
            createDigitalLocationWith(
              locationType = LocationType.IIIFPresentationAPI,
              license = Some(License.CCBYNC),
              accessConditions = List()
            )
          )
        )
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = data,
      relations = Relations(),
      availabilities = Set()
    )

    q.itemIds shouldBe List("item1111", "item2222")
    q.itemIdentifiers shouldBe List("sourceItem1", "sourceItem2", "otherItem2")
    q.itemAccessStatusIds shouldBe List(
      "open",
      "open-with-advisory",
      "closed",
      "open-with-advisory"
    )
    q.itemLicenseIds shouldBe List("cc-by", "cc-by-nc")
    q.itemLocationTypeIds shouldBe List(
      "open-shelves",
      "closed-stores",
      "iiif-image",
      "iiif-presentation"
    )
  }

  it("adds images") {
    val data = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      imageData = List(
        createImageDataWith(identifierValue = "sourceImage1")
          .toIdentifiedWith(canonicalId = CanonicalId("image111")),
        createImageDataWith(
          identifierValue = "sourceImage2",
          otherIdentifiers =
            List(createSourceIdentifierWith(value = "otherImage2")))
          .toIdentifiedWith(canonicalId = CanonicalId("image222"))
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = data,
      relations = Relations(),
      availabilities = Set()
    )

    q.imageIds shouldBe List("image111", "image222")
    q.imageIdentifiers shouldBe List(
      "sourceImage1",
      "sourceImage2",
      "otherImage2")
  }

  it("sets partOf") {
    val relations = Relations(
      ancestors = List(
        relation(id = Some("partOf11"), title = Some("The first relation")),
        relation(id = None, title = Some("The second relation")),
        relation(id = Some("partOf33"), title = None),
        relation(id = Some("partOf44"), title = Some("The fourth relation")),
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = WorkData[DataState.Identified](
        title = Some(s"title-${randomAlphanumeric(length = 10)}")
      ),
      relations = relations,
      availabilities = Set()
    )

    q.partOfIds shouldBe List("partOf11", "partOf33", "partOf44")
    q.partOfTitles shouldBe List(
      "The first relation",
      "The second relation",
      "The fourth relation")
  }

  it("adds availabilities") {
    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = WorkData[DataState.Identified](
        title = Some(s"title-${randomAlphanumeric(length = 10)}")
      ),
      relations = Relations(),
      availabilities = Set(
        Availability.Online,
        Availability.OpenShelves
      )
    )

    q.availabilityIds shouldBe List("online", "open-shelves")
  }

  it("adds production labels") {
    val workData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      production = List(
        ProductionEvent(
          label = "Percy publisher (top-level label => not included)",
          places = List(
            Place("Paris the place"),
            Place("Perth the port")
          ),
          agents = List(
            Person("Penny the press officer"),
          ),
          dates = List(
            Period(id = IdState.Unidentifiable, label = "The past")
          )
        ),
        ProductionEvent(
          label = "Patrick the pharmacist (top-level label => not included)",
          places = List(
            Place("Porto the Portuguese"),
          ),
          agents = List(
            Organisation("Purple People"),
            Meeting("Proactive Publicists"),
          ),
          dates = List(
            Period(id = IdState.Unidentifiable, label = "The rose-tinted past")
          )
        )
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = workData,
      relations = Relations(),
      availabilities = Set()
    )

    q.productionLabels shouldBe List(
      "Paris the place",
      "Perth the port",
      "Penny the press officer",
      "The past",
      "Porto the Portuguese",
      "Purple People",
      "Proactive Publicists",
      "The rose-tinted past"
    )
  }

  it("adds production dates as milliseconds-since-the-epoch") {
    val workData = WorkData[DataState.Identified](
      title = Some(s"title-${randomAlphanumeric(length = 10)}"),
      production = List(
        ProductionEvent(
          label = "Darren the Dastardly",
          places = List(),
          agents = List(),
          dates = List(
            Period(
              id = IdState.Unidentifiable,
              label = "The near future",
              range = Some(
                InstantRange(
                  from = LocalDate.of(2022, 9, 22),
                  to = LocalDate.of(2023, 9, 22),
                  label = "September 2022–23"))
            )
          )
        ),
        ProductionEvent(
          label = "Dana the Devilish",
          places = List(),
          agents = List(),
          dates = List(
            Period(
              id = IdState.Unidentifiable,
              label = "The distant future",
              range = Some(
                InstantRange(
                  from = LocalDate.of(2032, 9, 22),
                  to = LocalDate.of(2033, 9, 22),
                  label = "September 2032–33"))
            )
          )
        )
      )
    )

    val q = WorkQueryableValues(
      id = createCanonicalId,
      sourceIdentifier = createSourceIdentifier,
      workData = workData,
      relations = Relations(),
      availabilities = Set()
    )

    q.productionDatesRangeFrom shouldBe List(
      1663804800000L,
      1979424000000L
    )
  }

  private def relation(id: Option[String], title: Option[String]): Relation =
    Relation(
      id = id.map(CanonicalId(_)),
      title = title,
      collectionPath = None,
      workType = WorkType.Standard,
      depth = 1,
      numChildren = 0,
      numDescendents = 0,
    )
}
