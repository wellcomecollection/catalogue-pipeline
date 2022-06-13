package weco.pipeline.ingestor.works.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.locations.{AccessCondition, AccessMethod, AccessStatus, License, LocationType}
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  WorkGenerators
}

class WorkQueryableValuesTest
    extends AnyFunSpec
    with Matchers
    with ItemsGenerators
    with ImageGenerators
    with WorkGenerators {
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
    q.subjectIdentifiers shouldBe List("lcs-soggy", "mesh-soggy", "lcs-simon")
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

    q.genreConceptLabels shouldBe List("generosity", "greebles", "greatness")
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
      "open", "open-with-advisory", "closed", "open-with-advisory"
    )
    q.itemLicenseIds shouldBe List("cc-by", "cc-by-nc")
    q.itemLocationTypeIds shouldBe List(
      "open-shelves", "closed-stores", "iiif-image", "iiif-presentation"
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
