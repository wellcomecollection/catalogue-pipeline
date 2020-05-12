package uk.ac.wellcome.platform.api

import scala.reflect.runtime.universe._
import org.scalatest.matchers.should.Matchers
import akka.http.scaladsl.model.ContentTypes
import io.circe.Json
import uk.ac.wellcome.platform.api.models.SearchQueryType
import uk.ac.wellcome.platform.api.rest.{
  MultipleImagesParams,
  MultipleWorksParams,
  SingleImageParams,
  SingleWorkParams
}
import uk.ac.wellcome.platform.api.works.ApiWorksTestBase

class ApiSwaggerTest extends ApiWorksTestBase with Matchers {
  val worksEndpoint = "/works"
  val workEndpoint = "/works/{id}"
  val imageEndpoint = "/images/{id}"
  val imagesEndpoint = "/images"
  it("should return valid json object") {
    checkSwaggerJson { json =>
      json.isObject shouldBe true
    }
  }

  it("should contain info") {
    checkSwaggerJson { json =>
      val info = getKey(json, "info")
      info.isEmpty shouldBe false
      getKey(info.get, "version") shouldBe Some(Json.fromString("v2"))
      getKey(info.get, "description") shouldBe Some(
        Json.fromString("Search our collections"))
      getKey(info.get, "title") shouldBe Some(Json.fromString("Catalogue"))
    }
  }

  it("should contain servers") {
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

  it("should contain single work endpoint in paths") {
    checkSwaggerJson { json =>
      val endpoint = getEndpoint(json, workEndpoint)

      getKey(endpoint, "description").isEmpty shouldBe false
      getKey(endpoint, "summary").isEmpty shouldBe false
      val numParams = getKey(endpoint, "parameters")
        .flatMap(params => getLength(params))
      val numRouteParams = 1
      numParams shouldBe Some(
        getNumPublicQueryParams[SingleWorkParams] + numRouteParams
      )
    }
  }

  it("should contain multiple work endpoints in paths") {
    checkSwaggerJson { json =>
      val endpoint = getEndpoint(json, worksEndpoint)

      getKey(endpoint, "description").isEmpty shouldBe false
      getKey(endpoint, "summary").isEmpty shouldBe false
      val numParams = getKey(endpoint, "parameters")
        .flatMap(params => getLength(params))
      val numRouteParams = 0
      numParams shouldBe Some(
        getNumPublicQueryParams[MultipleWorksParams] + numRouteParams
      )
    }
  }

  it("should contain single image endpoint in paths") {
    checkSwaggerJson { json =>
      val endpoint = getEndpoint(json, imageEndpoint)

      getKey(endpoint, "description").isEmpty shouldBe false
      getKey(endpoint, "summary").isEmpty shouldBe false
      val numParams = getKey(endpoint, "parameters")
        .flatMap(params => getLength(params))
      val numRouteParams = 1
      numParams shouldBe Some(
        getNumPublicQueryParams[SingleImageParams] + numRouteParams
      )
    }
  }

  it("should contain multiple images endpoint in paths") {
    checkSwaggerJson { json =>
      val endpoint = getEndpoint(json, imagesEndpoint)

      getKey(endpoint, "description").isEmpty shouldBe false
      getKey(endpoint, "summary").isEmpty shouldBe false
      val numParams = getKey(endpoint, "parameters")
        .flatMap(params => getLength(params))
      val numRouteParams = 0
      numParams shouldBe Some(
        getNumPublicQueryParams[MultipleImagesParams] + numRouteParams
      )
    }
  }

  it("should contain schemas") {
    checkSwaggerJson { json =>
      val numSchemas = getKey(json, "components")
        .flatMap(components => getKey(components, "schemas"))
        .flatMap(getLength)
      numSchemas.isEmpty shouldBe false
      numSchemas.get should be > 20
    }
  }

  it("should not contain lots of List* schemas") {
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

  it("should not contain lots of Display* schemas") {
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

  it("should contain aggregation schemas") {
    checkSwaggerJson { json =>
      val schemas = getKey(json, "components")
        .flatMap(components => getKey(components, "schemas"))
        .map(getKeys)
      schemas.isEmpty shouldBe false
      schemas.get should contain allOf (
        "GenreAggregation",
        "WorkTypeAggregation",
        "PeriodAggregation",
        "SubjectAggregation",
        "LanguageAggregation",
        "GenreAggregationBucket",
        "WorkTypeAggregationBucket",
        "PeriodAggregationBucket",
        "SubjectAggregationBucket",
        "LanguageAggregationBucket",
      )
    }
  }

  //. We write this test for this specific parameter as it's used by the frontend
  it("should contain `_queryType parameter with valid `allowedValues`") {
    checkSwaggerJson { json =>
      val _queryType =
        getParameter(getEndpoint(json, worksEndpoint), "_queryType")

      val queryTypeAllowedValues =
        _queryType.get.hcursor
          .downField("schema")
          .get[List[String]]("enum")
          .toOption
          .getOrElse(List())

      queryTypeAllowedValues should contain theSameElementsAs (SearchQueryType.allowed
        .map(_.name))
    }
  }

  private def getParameter(endpoint: Json, name: String) =
    getKey(endpoint, "parameters")
      .flatMap(_.asArray)
      .flatMap(
        _.toList.find(getKey(_, "name").flatMap(_.asString).contains(name))
      )

  private def getEndpoint(json: Json, endpointString: String): Json = {
    val endpoint = getKey(json, "paths")
      .flatMap(paths => getKey(paths, endpointString))
      .flatMap(path => getKey(path, "get"))

    endpoint.isEmpty shouldBe false
    endpoint.get
  }

  private def getKeys(json: Json): List[String] =
    json.arrayOrObject(
      Nil,
      _ => Nil,
      obj => obj.keys.toList
    )

  private def getKey(json: Json, key: String): Option[Json] =
    json.arrayOrObject(
      None,
      _ => None,
      obj => obj.toMap.get(key)
    )

  private def getLength(json: Json): Option[Int] =
    json.arrayOrObject(
      None,
      arr => Some(arr.length),
      obj => Some(obj.keys.toList.length)
    )

  private def getNumPublicQueryParams[T: TypeTag]: Int =
    typeOf[T].members
      .collect {
        case m: MethodSymbol if m.isCaseAccessor => m.name.toString
      }
      .filterNot {
        _ == "_index"
      }
      .toList
      .length

  private def checkSwaggerJson(f: Json => Unit) =
    withApi {
      case (_, routes) =>
        Get(s"/$apiPrefix/swagger.json") ~> routes ~> check {
          status shouldEqual Status.OK
          contentType shouldEqual ContentTypes.`application/json`
          f(parseJson(responseAs[String]).toOption.get)
        }
    }
}
