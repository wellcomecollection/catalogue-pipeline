package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal._

class ApiV2WorksCollectionTest extends ApiV2WorksTestBase {

  def work(path: String, level: CollectionLevel) =
    createIdentifiedWorkWith(
      collection = Some(Collection(path = path, level = level)),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  val workA = work("a", CollectionLevel.Collection)
  val workB = work("a/b", CollectionLevel.Series)
  val workC = work("a/b/c", CollectionLevel.Item)
  val workD = work("a/d", CollectionLevel.Series)
  val workE = work("a/d/e", CollectionLevel.Item)

  def workJson(work: IdentifiedWork) =
    s"""
      {
        "id": "${work.canonicalId}",
        "alternativeTitles": [],
        "type": "Work",
        "title": "${work.data.title.get}"
      }
    """

  it("includes collection tree when requested") {
    withApi {
      case (index, routes) =>
        insertIntoElasticsearch(index, workA, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workC.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "a/b/c",
              "alternativeTitles": [],
              "collection": {
                "path": "a",
                "level": "Collection",
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": "a/b",
                    "level": "Series",
                    "work": ${workJson(workB)},
                    "children": [
                      {
                        "path": "a/b/c",
                        "level": "Item",
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": "a/d",
                    "level": "Series",
                    "work": ${workJson(workD)}
                  }
                ]
              }
            }
          """
        }
    }
  }

  it(
    "includes collection tree and additional multiple expanded paths when requested ") {
    withApi {
      case (index, routes) =>
        insertIntoElasticsearch(index, workA, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workB.canonicalId}?include=collection&_expandPaths=a/d,a/b/c") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workB.canonicalId}",
              "title": "a/b",
              "alternativeTitles": [],
              "collection": {
                "path": "a",
                "level": "Collection",
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": "a/b",
                    "level": "Series",
                    "work": ${workJson(workB)},
                    "children": [
                      {
                        "path": "a/b/c",
                        "level": "Item",
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": "a/d",
                    "level": "Series",
                    "work": ${workJson(workD)},
                    "children": [
                      {
                        "path": "a/d/e",
                        "level": "Item",
                        "work": ${workJson(workE)}
                      }
                    ]
                  }
                ]
              }
            }
          """
        }
    }
  }

  it("does not expand tree more than one level deep") {
    withApi {
      case (index, routes) =>
        insertIntoElasticsearch(index, workA, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workA.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workA.canonicalId}",
              "title": "a",
              "alternativeTitles": [],
              "collection": {
                "path": "a",
                "level": "Collection",
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": "a/b",
                    "level": "Series",
                    "work": ${workJson(workB)}
                  },
                  {
                    "path": "a/d",
                    "level": "Series",
                    "work": ${workJson(workD)}
                  }
                ]
              }
            }
          """
        }
    }
  }

  it("still returns the work when the collection tree cannot be generated") {
    withApi {
      case (index, routes) =>
        insertIntoElasticsearch(index, workA, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workC.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workC.canonicalId}",
              "title": "a/b/c",
              "alternativeTitles": []
            }
          """
        }
    }
  }

  it("does not include the collection tree when the root node is non existent") {
    withApi {
      case (index, routes) =>
        insertIntoElasticsearch(index, workB, workC, workD, workE)
        assertJsonResponse(
          routes,
          s"/$apiPrefix/works/${workE.canonicalId}?include=collection") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workE.canonicalId}",
              "title": "a/d/e",
              "alternativeTitles": []
            }
          """
        }
    }
  }
}
