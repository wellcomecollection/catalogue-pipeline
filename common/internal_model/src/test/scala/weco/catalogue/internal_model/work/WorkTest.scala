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
      "Attempt to decode value on failed cursor")
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

  it("concatenates the relation path when creating internal works") {
    val internalWorks = (1 to 5).map { i =>
      identifiedWork()
        .relationPath(RelationPath(path = s"inner/$i", label = None))
    } ++ (1 to 2).map { _ => identifiedWork() }

    val w = identifiedWork()
      .relationPath(
        RelationPath(path = "PP/ABC/1", label = "PPABC/1")
      )
      .mapState { state =>
        state.copy(internalWorkStubs = internalWorks.map { internalW =>
          InternalWork.Identified(
            sourceIdentifier = internalW.sourceIdentifier,
            canonicalId = internalW.state.canonicalId,
            workData = internalW.data
          )
        }.toList)
      }

    val expectedWorks = internalWorks.map { internalW =>
      val newRelationPath =
        internalW.data.relationPath.map { case RelationPath(path, label) =>
          RelationPath(path = s"PP/ABC/1/$path", label = label)
        }

      val newData = newRelationPath match {
        case Some(_) => internalW.data.copy(relationPath = newRelationPath)
        case None    => internalW.data
      }

      internalW
        .withVersion(version = 2)
        .copy(data = newData)
        .mapState { state =>
          state.copy(sourceModifiedTime = w.state.sourceModifiedTime)
        }
    }

    // If we get a relationPath from the parent work
    val actualWorks = w.state.internalWorksWith(parentRelationPath = w.data.relationPath, version = 2)

    actualWorks shouldBe expectedWorks

    // If we don't get a relationPath from the parent work
    w.state.internalWorksWith(parentRelationPath = None, version = 2) shouldBe
      internalWorks.map { internalW =>
        internalW
          .withVersion(version = 2)
          .mapState { state =>
            state.copy(sourceModifiedTime = w.state.sourceModifiedTime)
          }
      }
  }
}
