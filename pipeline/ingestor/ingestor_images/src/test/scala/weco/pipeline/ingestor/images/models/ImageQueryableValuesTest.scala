package weco.pipeline.ingestor.images.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.image.ParentWork
import weco.pipeline.ingestor.common.models.WorkQueryableValues

class ImageQueryableValuesTest
    extends AnyFunSpec
    with Matchers
    with ImageIngestorTestData {

  it("transforms images into queryable values") {
    val workSource = testImage.source.asInstanceOf[ParentWork]
    ImageQueryableValues(testImage) shouldBe ImageQueryableValues(
      id = "zswkgyan",
      source = WorkQueryableValues(
        canonicalId = workSource.id.canonicalId,
        sourceIdentifier = workSource.id.sourceIdentifier,
        data = workSource.data
      )
    )
  }

}
