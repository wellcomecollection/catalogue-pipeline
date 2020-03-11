package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.platform.api.works.ApiErrorsTestBase

class ApiV2ErrorsTest extends ApiV2WorksTestBase with ApiErrorsTestBase {

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
}
