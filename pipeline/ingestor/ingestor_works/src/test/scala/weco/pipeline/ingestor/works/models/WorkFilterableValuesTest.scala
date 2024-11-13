package weco.pipeline.ingestor.works.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.work._
import weco.pipeline.ingestor.models.IngestorTestData

class WorkFilterableValuesTest
    extends AnyFunSpec
    with Matchers
    with IngestorTestData {

  lazy val testWorkFilterableValues: WorkFilterableValues =
    WorkFilterableValues(testWork)

  it("creates filterable values") {
    testWorkFilterableValues shouldBe WorkFilterableValues(
      formatId = Some("k"),
      workType = "Standard",
      productionDatesRangeFrom = List(0),
      languagesId = List("eng"),
      genresLabel = List("Ink drawings", "Drawings"),
      genresConceptsId = List("h5fvmn9u", "tgxvuh8x"),
      genresConceptsSourceIdentifier = List("ink drawings", "drawings"),
      subjectsLabel = List(
        "Jungian psychology",
        "Dreams",
        "McGlashan, Alan Fleming, 1898-1997"
      ),
      subjectsConceptsId = List(
        "bse2dtxc",
        "hjw49bkh",
        "wfkwqmmx"
      ),
      subjectsConceptsSourceIdentifier = List("sh95006221", "sh85039483", "mcglashan, alan fleming, 1898-1997"),
      contributorsAgentLabel =
        List("M.A.C.T", "McGlashan, Alan Fleming, 1898-1997"),
      contributorsAgentId = List("npanm646", "wfkwqmmx"),
      contributorsAgentSourceIdentifier = List("m.a.c.t", "mcglashan, alan fleming, 1898-1997"),
      identifiersValue = List("b16582962", "1658296", "658296i", "L0045108"),
      itemsLocationsLicenseId = List("cc-by"),
      itemsLocationsAccessConditionsStatusId = List("open", "open"),
      itemsId = List("r8mz6j8c"),
      itemsIdentifiersValue = List("i15863207", "1586320"),
      itemsLocationsLocationTypeId = List("closed-stores", "iiif-image"),
      partOfId = List("hvkwjymu"),
      partOfTitle = List(
        "M.A.C.T.",
        "Dreams of a patient in Jungian analysis. Drawings, 1967-1978."
      ),
      availabilitiesId = List("closed-stores", "online")
    )
  }

  describe("adding ids from genre concepts") {
    def genreConceptWithCanonicalId(
      label: String,
      canonicalId: String
    ): GenreConcept[IdState.Identified] =
      GenreConcept[IdState.Identified](
        id = IdState.Identified(
          canonicalId = CanonicalId(canonicalId),
          sourceIdentifier = SourceIdentifier(
            IdentifierType.LabelDerived,
            ontologyType = "Genre",
            value = label
          ),
          otherIdentifiers = Nil
        ),
        label = label
      )

    def conceptWithCanonicalId(
      label: String,
      canonicalId: String
    ): Concept[IdState.Identified] =
      Concept[IdState.Identified](
        id = IdState.Identified(
          canonicalId = CanonicalId(canonicalId),
          sourceIdentifier = SourceIdentifier(
            IdentifierType.LabelDerived,
            ontologyType = "Concept",
            value = label
          ),
          otherIdentifiers = Nil
        ),
        label = label
      )

    it("produces an empty list if there are no genres") {
      WorkFilterableValues(
        testWork.copy[WorkState.Denormalised](data =
          testWork.data.copy[DataState.Identified](genres = Nil)
        )
      ).genresConceptsId shouldBe Nil
    }

    it(
      "produces a list consisting of the canonicalIds for each Genre concept"
    ) {
      WorkFilterableValues(
        testWork.copy[WorkState.Denormalised](data =
          testWork.data.copy[DataState.Identified](genres =
            List(
              Genre(
                label = "Etching",
                concepts = List(
                  genreConceptWithCanonicalId(
                    label = "Etching",
                    canonicalId = "qfsrdqed"
                  )
                )
              ),
              Genre(
                label = "Netsuke",
                concepts = List(
                  genreConceptWithCanonicalId(
                    label = "Netsuke",
                    canonicalId = "xe5nyufs"
                  )
                )
              )
            )
          )
        )
      ).genresConceptsId shouldBe List("qfsrdqed", "xe5nyufs")
    }

    it(
      "Only uses the first concept in each concepts list"
    ) {
      info("The first concept is the one pertaining to the actual genre.")
      info("Other concepts in the list are just extra information.")
      info("It may be worthwhile exposing them to queries,")
      info("but the known use-case for this field is to link between genres")
      info(
        "rather than (e.g.) cities or periods, which are inconsistently marked up in the source data"
      )

      WorkFilterableValues(
        testWork.copy[WorkState.Denormalised](data =
          testWork.data.copy[DataState.Identified](genres =
            List(
              Genre(
                label = "Etching",
                concepts = List(
                  genreConceptWithCanonicalId(
                    label = "Etching",
                    canonicalId = "qfsrdqed"
                  ),
                  conceptWithCanonicalId(
                    label = "Base 18th century humour",
                    canonicalId = "cyskv5me"
                  )
                )
              ),
              Genre(
                label = "Netsuke",
                concepts = List(
                  genreConceptWithCanonicalId(
                    label = "Netsuke",
                    canonicalId = "xe5nyufs"
                  ),
                  conceptWithCanonicalId(
                    label = "Tanuki",
                    canonicalId = "stqe4kw6"
                  )
                )
              )
            )
          )
        )
      ).genresConceptsId shouldBe List("qfsrdqed", "xe5nyufs")
    }

  }
}
