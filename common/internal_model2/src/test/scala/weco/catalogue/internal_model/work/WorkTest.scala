package weco.catalogue.internal_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil.{fromJson, toJson}
import weco.catalogue.internal_model.Implicits._
import weco.json.exceptions.JsonDecodingError
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.WorkGenerators

import java.time.Instant

class WorkTest extends AnyFunSpec with Matchers with WorkGenerators {

  // This is based on a real failure.  We deployed a version of the API
  // with a newer model than was in Elasticsearch -- in particular, it had
  // a newer Item definition.
  //
  // The result was a V1 API which didn't include any items, which caused
  // issues for /works pages on wellcomecollection.org.  This test checks
  // for "strictness" in our JSON parsing.
  //
  // The problem is that the "items" field has an outdated model -- it
  // should be wrapped in an "Identified" block, with the item data
  // inside an "agent" field.
  //
  it("fails to parse Work JSON with an outdated Item definition") {
    val jsonString =
      s"""
        |{
        |  "canonicalId": "$createCanonicalId",
        |  "sourceIdentifier": ${toJson(createSourceIdentifier).get},
        |  "otherIdentifiers": [],
        |  "mergeCandidates": [],
        |  "title": "${randomAlphanumeric(length = 10)}",
        |  "format": null,
        |  "description": null,
        |  "physicalDescription": null,
        |  "lettering": null,
        |  "createdDate": null,
        |  "subjects": [],
        |  "genres": [],
        |  "contributors": [],
        |  "thumbnail": null,
        |  "production": [],
        |  "language": null,
        |  "items": [
        |    {
        |      "canonicalId": "$createCanonicalId",
        |      "sourceIdentifier": ${toJson(createSourceIdentifier).get},
        |      "otherIdentifiers": [],
        |      "locations": []
        |    }
        |  ],
        |  "version": 1,
        |  "visible": true
        |}
      """.stripMargin

    val caught = intercept[JsonDecodingError] {
      fromJson[Work[WorkState.Identified]](jsonString).get
    }
    caught.getMessage should startWith(
      "Missing required field"
    )
  }

  it("preserves redirect sources when transitioning Work.Visible") {
    val redirectSources = (1 to 3).map { _ =>
      IdState.Identified(createCanonicalId, createSourceIdentifier)
    }

    val w = identifiedWork().withRedirectSources(redirectSources)

    w.transition[Merged](Instant.now)
      .asInstanceOf[Work.Visible[Merged]]
      .redirectSources shouldBe redirectSources
  }
}
