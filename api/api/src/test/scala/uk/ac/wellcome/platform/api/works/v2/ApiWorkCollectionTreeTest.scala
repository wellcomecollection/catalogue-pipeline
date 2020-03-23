package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal._

class ApiWorkCollectionTreeTest extends ApiV2WorksTestBase {

  def work(path: String) =
    createIdentifiedWorkWith(
      collection = Some(Collection(path = path)),
      title = Some(path),
      sourceIdentifier = createSourceIdentifierWith(value = path)
    )

  val workA = work("a")
  val workB = work("a/b")
  val workC = work("a/b/c")
  val workD = work("a/d")
  val workE = work("a/d/e")

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
                "path": "a/b/c",
                "type": "Collection"
              },
              "collectionTree": {
                "path": "a",
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": "a/b",
                    "work": ${workJson(workB)},
                    "children": [
                      {
                        "path": "a/b/c",
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": "a/d",
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
          s"/$apiPrefix/works/${workB.canonicalId}?include=collection&expandPaths=a/d,a/b/c") {
          Status.OK -> s"""
            {
              ${singleWorkResult(apiPrefix)},
              "id": "${workB.canonicalId}",
              "title": "a/b",
              "alternativeTitles": [],
              "collection": {
                "path": "a/b",
                "type": "Collection"
              },
              "collectionTree": {
                "path": "a",
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": "a/b",
                    "work": ${workJson(workB)},
                    "children": [
                      {
                        "path": "a/b/c",
                        "work": ${workJson(workC)},
                        "children": []
                      }
                    ]
                  },
                  {
                    "path": "a/d",
                    "work": ${workJson(workD)},
                    "children": [
                      {
                        "path": "a/d/e",
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
                "type": "Collection"
              },
              "collectionTree": {
                "path": "a",
                "work": ${workJson(workA)},
                "children": [
                  {
                    "path": "a/b",
                    "work": ${workJson(workB)}
                  },
                  {
                    "path": "a/d",
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
              "alternativeTitles": [],
              "collection": {
                "path": "a/b/c",
                "type": "Collection"
              }
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
              "alternativeTitles": [],
              "collection": {
                "path": "a/d/e",
                "type": "Collection"
              }
            }
          """
        }
    }
  }
}
