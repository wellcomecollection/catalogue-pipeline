package uk.ac.wellcome.platform.api.works

class WorksErrorsTest extends ApiWorksTestBase {

  describe("returns a 400 Bad Request for errors in the ?include parameter") {
    it("a single invalid include") {
      assertIsBadRequest(
        "/works?include=foo",
        description =
          "include: 'foo' is not a valid value. Please choose one of: ['identifiers', 'items', 'subjects', 'genres', 'contributors', 'production', 'notes', 'collection']"
      )
    }

    it("multiple invalid includes") {
      assertIsBadRequest(
        "/works?include=foo,bar",
        description =
          "include: 'foo', 'bar' are not valid values. Please choose one of: ['identifiers', 'items', 'subjects', 'genres', 'contributors', 'production', 'notes', 'collection']"
      )
    }

    it("a mixture of valid and invalid includes") {
      assertIsBadRequest(
        "/works?include=foo,identifiers,bar",
        description =
          "include: 'foo', 'bar' are not valid values. Please choose one of: ['identifiers', 'items', 'subjects', 'genres', 'contributors', 'production', 'notes', 'collection']"
      )
    }

    it("an invalid include on an individual work") {
      assertIsBadRequest(
        "/works/nfdn7wac?include=foo",
        description =
          "include: 'foo' is not a valid value. Please choose one of: ['identifiers', 'items', 'subjects', 'genres', 'contributors', 'production', 'notes', 'collection']"
      )
    }
  }

  describe(
    "returns a 400 Bad Request for errors in the ?aggregations parameter") {
    it("a single invalid aggregation") {
      assertIsBadRequest(
        "/works?aggregations=foo",
        description =
          "aggregations: 'foo' is not a valid value. Please choose one of: ['workType', 'genres', 'production.dates', 'subjects', 'language', 'license']"
      )
    }

    it("multiple invalid aggregations") {
      assertIsBadRequest(
        "/works?aggregations=foo,bar",
        description =
          "aggregations: 'foo', 'bar' are not valid values. Please choose one of: ['workType', 'genres', 'production.dates', 'subjects', 'language', 'license']"
      )
    }

    it("multiple invalid sorts") {
      assertIsBadRequest(
        "/works?sort=foo,bar",
        description =
          "sort: 'foo', 'bar' are not valid values. Please choose one of: ['production.dates']"
      )
    }

    it("a mixture of valid and invalid aggregations") {
      assertIsBadRequest(
        "/works?aggregations=foo,workType,bar",
        description =
          "aggregations: 'foo', 'bar' are not valid values. Please choose one of: ['workType', 'genres', 'production.dates', 'subjects', 'language', 'license']"
      )
    }
  }

  describe("returns a 400 Bad Request for errors in the ?sort parameter") {
    it("a single invalid sort") {
      assertIsBadRequest(
        "/works?sort=foo",
        description =
          "sort: 'foo' is not a valid value. Please choose one of: ['production.dates']"
      )
    }

    it("multiple invalid sorts") {
      assertIsBadRequest(
        "/works?sort=foo,bar",
        description =
          "sort: 'foo', 'bar' are not valid values. Please choose one of: ['production.dates']"
      )
    }

    it("a mixture of valid and invalid sort") {
      assertIsBadRequest(
        "/works?sort=foo,production.dates,bar",
        description =
          "sort: 'foo', 'bar' are not valid values. Please choose one of: ['production.dates']"
      )
    }

    it("400s on a non-int-able value for collection.depth") {
      assertIsBadRequest(
        "/works?collection.depth=challenger-deep",
        description = "collection.depth: must be a valid Integer"
      )
    }
  }

  // This is expected as it's transient parameter that will have valid values changing over time
  // And if there is a client with a deprecated value, we wouldn't want it to fail
  describe("returns a 200 for invalid values in the ?_queryType parameter") {
    it("200s despite being a unknown value") {
      withApi {
        case (_, routes) =>
          assertJsonResponse(
            routes,
            s"/$apiPrefix/works?_queryType=athingwewouldneverusebutmightbecausewesaidwewouldnot") {
            Status.OK -> emptyJsonResult(apiPrefix)
          }
      }
    }
  }

  describe("returns a 400 Bad Request for user errors") {
    describe("errors in the ?pageSize query") {
      it("not an integer") {
        val pageSize = "penguin"
        assertIsBadRequest(
          s"/works?pageSize=$pageSize",
          description = s"pageSize: must be a valid Integer"
        )
      }

      it("just over the maximum") {
        val pageSize = 101
        assertIsBadRequest(
          s"/works?pageSize=$pageSize",
          description = "pageSize: must be between 1 and 100"
        )
      }

      it("just below the minimum (zero)") {
        val pageSize = 0
        assertIsBadRequest(
          s"/works?pageSize=$pageSize",
          description = "pageSize: must be between 1 and 100"
        )
      }

      it("a large page size") {
        val pageSize = 100000
        assertIsBadRequest(
          s"/works?pageSize=$pageSize",
          description = "pageSize: must be between 1 and 100"
        )
      }

      it("a negative page size") {
        val pageSize = -50
        assertIsBadRequest(
          s"/works?pageSize=$pageSize",
          description = "pageSize: must be between 1 and 100"
        )
      }
    }

    describe("errors in the ?page query") {
      it("page 0") {
        val page = 0
        assertIsBadRequest(
          s"/works?page=$page",
          description = "page: must be greater than 1"
        )
      }

      it("a negative page") {
        val page = -50
        assertIsBadRequest(
          s"/works?page=$page",
          description = "page: must be greater than 1"
        )
      }
    }

    describe("trying to get more works than ES allows") {
      val description = "Only the first 10000 works are available in the API. " +
        "If you want more works, you can download a snapshot of the complete catalogue: " +
        "https://developers.wellcomecollection.org/datasets"

      it("a very large page") {
        assertIsBadRequest(
          s"/works?page=10000",
          description = description
        )
      }

      // https://github.com/wellcometrust/platform/issues/3233
      it("so many pages that a naive (page * pageSize) would overflow") {
        assertIsBadRequest(
          s"/works?page=2000000000&pageSize=100",
          description = description
        )
      }

      it("the 101th page with 100 results per page") {
        assertIsBadRequest(
          s"/works?page=101&pageSize=100",
          description = description
        )
      }
    }

    it("returns multiple errors if there's more than one invalid parameter") {
      val pageSize = -60
      val page = -50
      assertIsBadRequest(
        s"/works?pageSize=$pageSize&page=$page",
        description =
          "page: must be greater than 1, pageSize: must be between 1 and 100"
      )
    }
  }

  describe("returns a 404 Not Found for missing resources") {
    it("looking up a work that doesn't exist") {
      val badId = "doesnotexist"
      assertIsNotFound(
        s"/works/$badId",
        description = s"Work not found for identifier $badId"
      )
    }

    it("looking up a work with a malformed identifier") {
      val badId = "zd224ncv]"
      assertIsNotFound(
        s"/works/$badId",
        description = s"Work not found for identifier $badId"
      )
    }

    describe("an index that doesn't exist") {
      val indexName = "foobarbaz"

      it("listing") {
        assertIsNotFound(
          s"/works?_index=$indexName",
          description = s"There is no index $indexName"
        )
      }

      it("looking up a work") {
        assertIsNotFound(
          s"/works/1234?_index=$indexName",
          description = s"There is no index $indexName"
        )
      }

      it("searching") {
        assertIsNotFound(
          s"/works/1234?_index=$indexName&query=foobar",
          description = s"There is no index $indexName"
        )
      }
    }
  }

  it("returns an Internal Server error if you try to search a malformed index") {
    // We need to do something that reliably triggers an internal exception
    // in the Elasticsearch handler.
    //
    // By creating an index without a mapping, we don't have a canonicalId field
    // to sort on.  Trying to query this index of these will trigger one such exception!
    withApi {
      case (_, routes) =>
        withEmptyIndex { index =>
          val path = s"/${getApiPrefix()}/works?_index=${index.name}"
          assertJsonResponse(routes, path)(
            Status.InternalServerError ->
              s"""
                 |{
                 |  "@context": "${contextUrl(getApiPrefix())}",
                 |  "type": "Error",
                 |  "errorType": "http",
                 |  "httpStatus": 500,
                 |  "label": "Internal Server Error"
                 |}
            """.stripMargin
          )
        }
    }
  }
}
