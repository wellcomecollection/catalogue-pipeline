package uk.ac.wellcome.platform.api

import org.scalatest.matchers.should.Matchers
import akka.http.scaladsl.model.ContentTypes
import io.circe.Json
import org.scalatest.prop.TableDrivenPropertyChecks
import uk.ac.wellcome.display.models.{SingleImageIncludes, WorksIncludes}
import uk.ac.wellcome.platform.api.fixtures.ReflectionHelpers
import uk.ac.wellcome.platform.api.models.{
  DisplayWorkAggregations,
  SearchQueryType,
  WorkAggregations
}
import uk.ac.wellcome.platform.api.rest._
import uk.ac.wellcome.platform.api.works.ApiWorksTestBase

class ApiSwaggerTest
    extends ApiWorksTestBase
    with Matchers
    with JsonHelpers
    with ReflectionHelpers
    with TableDrivenPropertyChecks {

  val multipleWorksEndpoint = "/works"
  val singleWorkEndpoint = "/works/{id}"
  val singleImageEndpoint = "/images/{id}"
  val multipleImagesEndpoint = "/images"

  it("returns a JSON object") {
    checkSwaggerJson { json =>
      json.isObject shouldBe true
    }
  }

  it("contains 'info'") {
    checkSwaggerJson { json =>
      val info = getKey(json, "info")
      info.isEmpty shouldBe false
      getKey(info.get, "version") shouldBe Some(Json.fromString("v2"))
      getKey(info.get, "description") shouldBe Some(
        Json.fromString("Search our collections"))
      getKey(info.get, "title") shouldBe Some(Json.fromString("Catalogue"))
    }
  }

  it("contains 'servers'") {
    checkSwaggerJson { json =>
      getKey(json, "servers") shouldBe Some(
        Json.arr(
          Json.obj(
            "url" -> Json.fromString("https://api-testing.local/catalogue/v2/")
          )
        )
      )
    }
  }

  it("sets a non-empty description and summary on all paths") {
    val testCases = Table(
      "endpoint",
      singleWorkEndpoint,
      multipleWorksEndpoint,
      singleImageEndpoint,
      multipleImagesEndpoint
    )

    forAll(testCases) { endpointString =>
      checkSwaggerJson { json =>
        val endpoint = getEndpoint(json, endpointString)

        getKey(endpoint, "description").isEmpty shouldBe false
        getKey(endpoint, "summary").isEmpty shouldBe false
      }
    }
  }

  describe("includes the route and query parameters on all paths") {
    it("single work endpoint") {
      val swaggerParams = getParameterNames(singleWorkEndpoint)

      val routeParam = "id"
      val queryParams = getFields[SingleWorkParams]

      val internalParams = queryParams :+ routeParam

      assert(
        swaggerParams.length == internalParams.length,
        s"swaggerParams  = ${swaggerParams.sorted}\ninternalParams = ${internalParams.sorted}"
      )
    }

    it("multiple works endpoint") {
      val swaggerParams = getParameterNames(multipleWorksEndpoint)
      val queryParams = getFields[MultipleWorksParams]

      assert(
        swaggerParams.length == queryParams.length,
        s"swaggerParams = ${swaggerParams.sorted}\nactualParams  = ${queryParams.sorted}"
      )
    }

    it("single image endpoint") {
      val swaggerParams = getParameterNames(singleImageEndpoint)

      val routeParam = "id"
      val queryParams = getFields[SingleImageParams]

      val internalParams = queryParams :+ routeParam

      assert(
        swaggerParams.length == internalParams.length,
        s"swaggerParams  = ${swaggerParams.sorted}\ninternalParams = ${internalParams.sorted}"
      )
    }

    it("multiple images endpoint") {
      val swaggerParams = getParameterNames(multipleImagesEndpoint)

      val queryParams = getFields[MultipleImagesParams]

      assert(
        swaggerParams.length == queryParams.length,
        s"swaggerParams = ${swaggerParams.sorted}\nactualParams  = ${queryParams.sorted}"
      )
    }

    def getParameterNames(endpointString: String): List[String] = {
      val names =
        getParameters(endpointString)
          .flatMap { getKey(_, "name") }
          .map { _.asString.get }
          .toList

      assertDistinct(names)
      names
    }
  }

  describe("lists all the available includes on all paths") {
    it("single work endpoint") {
      val swaggerIncludes = getSwaggerIncludes(singleWorkEndpoint)

      val workIncludes = getFields[WorksIncludes]

      swaggerIncludes should contain theSameElementsAs workIncludes
    }

    it("multiple works endpoint") {
      val swaggerIncludes = getSwaggerIncludes(multipleWorksEndpoint)

      val workIncludes = getFields[WorksIncludes]

      swaggerIncludes should contain theSameElementsAs workIncludes
    }

    // We don't currently have any ?include= fields on the multiple
    // images endpoint.  If that changes, we should add a new test.

    it("single image endpoint") {
      val swaggerIncludes = getSwaggerIncludes(singleImageEndpoint)

      val workIncludes = getFields[SingleImageIncludes]

      swaggerIncludes should contain theSameElementsAs workIncludes
    }

    def getSwaggerIncludes(endpointString: String): Seq[String] = {
      val includeParam =
        getParameters(endpointString).filter {
          getKey(_, "name").get.asString.contains("include")
        }.head

      getEnumValues(includeParam)
    }
  }

  private def getParameters(endpointString: String): Seq[Json] =
    checkSwaggerJson { json =>
      val endpointSwagger = getEndpoint(json, endpointString)

      getKey(endpointSwagger, "parameters").flatMap { _.asArray }.get
    }

  it("lists all the available aggregations") {
    val aggregationsParam =
      getParameters(multipleWorksEndpoint).filter {
        getKey(_, "name").get.asString.contains("aggregations")
      }.head

    val swaggerParams = getEnumValues(aggregationsParam)
    val aggregationParams = getFields[WorkAggregations]

    assert(
      swaggerParams.length == aggregationParams.length,
      s"swaggerParams     = ${swaggerParams.sorted}\naggregationParams = ${aggregationParams.sorted}"
    )
  }

  // Given a JSON object of the form:
  //
  //      {
  //        "schema" : {
  //          "enum" : ["value1", "value2", "value3"],
  //          ...
  //        },
  //        ...
  //      }
  //
  // return the list of strings in the 'enum'.
  private def getEnumValues(json: Json): Seq[String] =
    getKey(json, "schema")
      .flatMap { getKey(_, "enum") }
      .flatMap { _.asArray }
      .get
      .map { _.asString.get }

  it("contains 'schemas'") {
    checkSwaggerJson { json =>
      val numSchemas = getKey(json, "components")
        .flatMap(components => getKey(components, "schemas"))
        .flatMap(getLength)
      numSchemas.isEmpty shouldBe false
      numSchemas.get should be > 20
    }
  }

  it("does not contain lots of List* schemas") {
    checkSwaggerJson { json =>
      val listSchemas = getKey(json, "components")
        .flatMap(components => getKey(components, "schemas"))
        .map { schemas =>
          getKeys(schemas).filter(key => key.startsWith("List"))
        }
      listSchemas.isEmpty shouldBe false
      listSchemas.get shouldBe Nil
    }
  }

  it("does not contain lots of Display* schemas") {
    checkSwaggerJson { json =>
      val listSchemas = getKey(json, "components")
        .flatMap(components => getKey(components, "schemas"))
        .map { schemas =>
          getKeys(schemas).filter(key => key.startsWith("Display"))
        }
      listSchemas.isEmpty shouldBe false
      listSchemas.get shouldBe Nil
    }
  }

  it("contains aggregation schemas") {
    checkSwaggerJson { json =>
      val schemas = getKey(json, "components")
        .flatMap(components => getKey(components, "schemas"))
        .map(getKeys)
      schemas.isEmpty shouldBe false
      schemas.get should contain allOf (
        "GenreAggregation",
        "FormatAggregation",
        "PeriodAggregation",
        "SubjectAggregation",
        "LanguageAggregation",
        "GenreAggregationBucket",
        "FormatAggregationBucket",
        "PeriodAggregationBucket",
        "SubjectAggregationBucket",
        "LanguageAggregationBucket",
        "LicenseAggregationBucket",
      )
    }
  }

  describe("includes bounds for the pageSize parameter") {
    it("images endpoint") {
      checkBoundsOnPagesizeParameters(multipleImagesEndpoint)
    }

    it("works endpoint") {
      checkBoundsOnPagesizeParameters(multipleWorksEndpoint)
    }

    def checkBoundsOnPagesizeParameters(endpointString: String): Unit = {
      checkSwaggerJson { json =>
        val endpoint = getEndpoint(json, endpointString = endpointString)

        val pageSizeParam = getParameter(endpoint, name = "pageSize").get

        val schema = getKey(pageSizeParam, key = "schema").get

        getNumericKey(schema, key = "minimum") shouldBe Some(
          PaginationLimits.minSize)
        getNumericKey(schema, key = "maximum") shouldBe Some(
          PaginationLimits.maxSize)
        getNumericKey(schema, key = "default") shouldBe Some(10)
        getKey(schema, key = "type").flatMap { _.asString } shouldBe Some(
          "integer")
      }
    }
  }

  describe("includes bounds for the page parameter") {
    it("images endpoint") {
      checkPageParameter(multipleImagesEndpoint)
    }

    it("works endpoint") {
      checkPageParameter(multipleWorksEndpoint)
    }

    def checkPageParameter(endpointString: String): Unit = {
      checkSwaggerJson { json =>
        val endpoint = getEndpoint(json, endpointString = endpointString)

        val pageSizeParam = getParameter(endpoint, name = "page").get

        val schema = getKey(pageSizeParam, key = "schema").get

        getNumericKey(schema, key = "minimum") shouldBe Some(1)
        getNumericKey(schema, key = "default") shouldBe Some(1)
        getKey(schema, key = "type").flatMap { _.asString } shouldBe Some(
          "integer")
      }
    }
  }

  it("lists the properties on the Aggregations model") {
    checkSwaggerJson { json =>
      val schemas =
        getKey(json, "components")
          .flatMap { getKey(_, "schemas") }
          .get

      val aggregationsProperties =
        getKey(schemas, "Aggregations")
          .flatMap { getKey(_, "properties") }
          .get

      getKeys(aggregationsProperties) should contain("type")
      val displayFields = getKeys(aggregationsProperties)
        .filterNot { _ == "type" }

      getFields[DisplayWorkAggregations] should contain("ontologyType")
      val internalFields = getFields[DisplayWorkAggregations]
        .filterNot { _ == "ontologyType"}

      displayFields should contain theSameElementsAs internalFields
    }
  }

  //. We write this test for this specific parameter as it's used by the frontend
  it("contains the `_queryType parameter with valid `allowedValues`") {
    checkSwaggerJson { json =>
      val _queryType =
        getParameter(getEndpoint(json, multipleWorksEndpoint), "_queryType")

      val queryTypeAllowedValues =
        _queryType.get.hcursor
          .downField("schema")
          .get[List[String]]("enum")
          .toOption
          .getOrElse(List())

      queryTypeAllowedValues should contain theSameElementsAs SearchQueryType.allowed
        .map(_.name)
    }
  }

  private def assertDistinct(values: Seq[Any]) =
    assert(
      values.distinct.size == values.size,
      s"Duplicates: ${values.filter { v =>
        values.count(v == _) > 1
      }.distinct}"
    )

  private def checkSwaggerJson[T](f: Json => T): T =
    withApi { routes =>
      Get(s"/$apiPrefix/swagger.json") ~> routes ~> check {
        status shouldEqual Status.OK
        contentType shouldEqual ContentTypes.`application/json`
        f(parseJson(responseAs[String]))
      }
    }
}
