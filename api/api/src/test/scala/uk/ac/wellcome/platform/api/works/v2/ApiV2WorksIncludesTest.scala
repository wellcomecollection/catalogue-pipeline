package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.generators.{
  ProductionEventGenerators,
  SubjectGenerators
}
import uk.ac.wellcome.models.work.internal._

class ApiV2WorksIncludesTest
    extends ApiV2WorksTestBase
    with ProductionEventGenerators
    with SubjectGenerators {

  it(
    "includes a list of identifiers on a list endpoint if we pass ?include=identifiers") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 2).sortBy { _.canonicalId }

        val identifier0 = createSourceIdentifier
        val identifier1 = createSourceIdentifier

        val work0 = works(0).copy(otherIdentifiers = List(identifier0))
        val work1 = works(1).copy(otherIdentifiers = List(identifier1))

        insertIntoElasticsearch(indexV2, work0, work1)

        assertJsonResponse(routes, s"/$apiPrefix/works?include=identifiers") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 2)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work0.canonicalId}",
                 "title": "${work0.title}",
                 "identifiers": [
                   ${identifier(work0.sourceIdentifier)},
                   ${identifier(identifier0)}
                 ]
               },
               {
                 "type": "Work",
                 "id": "${work1.canonicalId}",
                 "title": "${work1.title}",
                 "identifiers": [
                   ${identifier(work1.sourceIdentifier)},
                   ${identifier(identifier1)}
                 ]
               }
              ]
            }
          """
        }
    }
  }

  it(
    "includes a list of identifiers on a single work endpoint if we pass ?include=identifiers") {
    withApi {
      case (indexV2, routes) =>
        val otherIdentifier = createSourceIdentifier
        val work = createIdentifiedWorkWith(
          otherIdentifiers = List(otherIdentifier)
        )
        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=identifiers") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "identifiers": [
                ${identifier(work.sourceIdentifier)},
                ${identifier(otherIdentifier)}
              ]
            }
          """
        }
    }
  }

  it("renders the items if the items include is present") {
    withApi {
      case (indexV2, routes) =>
        val work = createIdentifiedWorkWith(
          items = createIdentifiedItems(count = 1) :+ createUnidentifiableItemWith()
        )

        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=items") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "items": [ ${items(work.items)} ]
            }
          """
        }
    }
  }

  it(
    "includes a list of subjects on a list endpoint if we pass ?include=subjects") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 2).sortBy { _.canonicalId }

        val subjects1 = List(createSubject)
        val subjects2 = List(createSubject)
        val work0 = works(0).copy(subjects = subjects1)
        val work1 = works(1).copy(subjects = subjects2)

        insertIntoElasticsearch(indexV2, work0, work1)

        assertJsonResponse(routes, s"/$apiPrefix/works?include=subjects") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 2)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work0.canonicalId}",
                 "title": "${work0.title}",
                 "subjects": [ ${subjects(subjects1)}]
               },
               {
                 "type": "Work",
                 "id": "${work1.canonicalId}",
                 "title": "${work1.title}",
                 "subjects": [ ${subjects(subjects2)}]
               }
              ]
            }
          """
        }
    }
  }

  it(
    "includes a list of subjects on a single work endpoint if we pass ?include=subjects") {
    withApi {
      case (indexV2, routes) =>
        val subject = List(createSubject)
        val work = createIdentifiedWork.copy(subjects = subject)

        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=subjects") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "subjects": [ ${subjects(subject)}]
            }
          """
        }
    }
  }

  it("includes a list of genres on a list endpoint if we pass ?include=genres") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 2).sortBy { _.canonicalId }

        val genres1 = List(
          Genre("ornithology", List(Unidentifiable(Concept("ornithology")))))
        val genres2 = List(
          Genre("flying cars", List(Unidentifiable(Concept("flying cars")))))
        val work0 = works(0).copy(genres = genres1)
        val work1 = works(1).copy(genres = genres2)

        insertIntoElasticsearch(indexV2, work0, work1)

        assertJsonResponse(routes, s"/$apiPrefix/works?include=genres") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 2)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work0.canonicalId}",
                 "title": "${work0.title}",
                 "genres": [ ${genres(genres1)}]
               },
               {
                 "type": "Work",
                 "id": "${work1.canonicalId}",
                 "title": "${work1.title}",
                 "genres": [ ${genres(genres2)}]
               }
              ]
            }
          """
        }
    }
  }

  it(
    "includes a list of genres on a single work endpoint if we pass ?include=genres") {
    withApi {
      case (indexV2, routes) =>
        val genre = List(
          Genre("ornithology", List(Unidentifiable(Concept("ornithology")))))
        val work = createIdentifiedWork.copy(genres = genre)

        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=genres") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "genres": [ ${genres(genre)}]
            }
          """
        }
    }
  }

  it(
    "includes a list of contributors on a list endpoint if we pass ?include=contributors") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 2).sortBy { _.canonicalId }

        val contributors1 =
          List(Contributor(Unidentifiable(Person("Ginger Rogers"))))
        val contributors2 =
          List(Contributor(Unidentifiable(Person("Fred Astair"))))
        val work0 = works(0).copy(contributors = contributors1)
        val work1 = works(1).copy(contributors = contributors2)

        insertIntoElasticsearch(indexV2, work0, work1)

        assertJsonResponse(routes, s"/$apiPrefix/works/?include=contributors") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 2)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work0.canonicalId}",
                 "title": "${work0.title}",
                 "contributors": [ ${contributors(contributors1)}]
               },
               {
                 "type": "Work",
                 "id": "${work1.canonicalId}",
                 "title": "${work1.title}",
                 "contributors": [ ${contributors(contributors2)}]
               }
              ]
            }
          """
        }
    }
  }

  it(
    "includes a list of contributors on a single work endpoint if we pass ?include=contributors") {
    withApi {
      case (indexV2, routes) =>
        val contributor =
          List(Contributor(Unidentifiable(Person("Ginger Rogers"))))
        val work = createIdentifiedWork.copy(contributors = contributor)

        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=contributors") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "contributors": [ ${contributors(contributor)}]
            }
          """
        }
    }
  }

  it(
    "includes a list of production events on a list endpoint if we pass ?include=production") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 2).sortBy { _.canonicalId }

        val productionEvents1 = createProductionEventList(count = 1)
        val productionEvents2 = createProductionEventList(count = 2)
        val work0 = works(0).copy(production = productionEvents1)
        val work1 = works(1).copy(production = productionEvents2)

        insertIntoElasticsearch(indexV2, work0, work1)

        assertJsonResponse(routes, s"/$apiPrefix/works?include=production") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 2)},
              "results": [
               {
                 "type": "Work",
                 "id": "${work0.canonicalId}",
                 "title": "${work0.title}",
                 "production": [ ${production(productionEvents1)}]
               },
               {
                 "type": "Work",
                 "id": "${work1.canonicalId}",
                 "title": "${work1.title}",
                 "production": [ ${production(productionEvents2)}]
               }
              ]
            }
          """
        }
    }
  }

  it(
    "includes a list of production on a single work endpoint if we pass ?include=production") {
    withApi {
      case (indexV2, routes) =>
        val productionEventList = createProductionEventList()
        val work = createIdentifiedWorkWith(
          production = productionEventList
        )

        insertIntoElasticsearch(indexV2, work)

        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=production") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "production": [ ${production(productionEventList)}]
            }
          """
        }
    }
  }

  it("includes notes on the list endpoint if we pass ?include=notes") {
    withApi {
      case (indexV2, routes) =>
        val works = List(
          createIdentifiedWorkWith(
            canonicalId = "A",
            notes = List(GeneralNote("A"), FundingInformation("B"))),
          createIdentifiedWorkWith(
            canonicalId = "B",
            notes = List(GeneralNote("C"), GeneralNote("D"))),
        )
        insertIntoElasticsearch(indexV2, works: _*)
        assertJsonResponse(routes, s"/$apiPrefix/works?include=notes") {
          Status.OK -> s"""
            {
              ${resultList(apiPrefix, totalResults = 2)},
              "results": [
                 {
                   "type": "Work",
                   "id": "${works(0).canonicalId}",
                   "title": "${works(0).title}",
                   "notes": [
                     {
                       "noteType": {
                         "id": "funding-info",
                         "label": "Funding information",
                         "type": "NoteType"
                       },
                       "contents": ["B"],
                       "type": "Note"
                     },
                     {
                       "noteType": {
                         "id": "general-note",
                         "label": "General note",
                         "type": "NoteType"
                       },
                       "contents": ["A"],
                       "type": "Note"
                     }
                   ]
                 },
                 {
                   "type": "Work",
                   "id": "${works(1).canonicalId}",
                   "title": "${works(1).title}",
                   "notes": [
                     {
                       "noteType": {
                         "id": "general-note",
                         "label": "General note",
                         "type": "NoteType"
                       },
                       "contents": ["C", "D"],
                       "type": "Note"
                     }
                   ]
                }
              ]
            }
          """
        }
    }
  }

  it("includes notes on the single work endpoint if we pass ?include=notes") {
    withApi {
      case (indexV2, routes) =>
        val work = createIdentifiedWorkWith(
          notes = List(GeneralNote("A"), GeneralNote("B")))
        insertIntoElasticsearch(indexV2, work)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${work.canonicalId}?include=notes") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${work.canonicalId}",
              "title": "${work.title}",
              "notes": [
                 {
                   "noteType": {
                     "id": "general-note",
                     "label": "General note",
                     "type": "NoteType"
                   },
                   "contents": ["A", "B"],
                   "type": "Note"
                 }
              ]
            }
          """
        }
    }
  }
}
