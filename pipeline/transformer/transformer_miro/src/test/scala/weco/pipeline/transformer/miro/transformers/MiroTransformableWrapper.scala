package weco.pipeline.transformer.miro.transformers

import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, Suite}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.MiroRecordTransformer
import weco.pipeline.transformer.miro.exceptions.MiroTransformerException
import weco.pipeline.transformer.miro.models.MiroMetadata
import weco.pipeline.transformer.miro.source.MiroRecord

import scala.util.Try

trait MiroTransformableWrapper extends Matchers { this: Suite =>
  val transformer = new MiroRecordTransformer

  def transformWork(
    miroRecord: MiroRecord,
    overrides: MiroSourceOverrides = MiroSourceOverrides.empty
  ): Work.Visible[Source] = {
    val triedWork: Try[Work[Source]] =
      transformer.transform(
        miroRecord = miroRecord,
        overrides = overrides,
        miroMetadata = MiroMetadata(isClearedForCatalogueAPI = true),
        version = 1
      )

    if (triedWork.isFailure) {
      triedWork.failed.get.printStackTrace()
      println(
        triedWork.failed.get
          .asInstanceOf[MiroTransformerException]
          .e
          .getMessage
      )
    }

    triedWork.isSuccess shouldBe true
    triedWork.get.asInstanceOf[Work.Visible[Source]]
  }

  def assertTransformWorkFails(miroRecord: MiroRecord): Assertion =
    transformer
      .transform(
        miroRecord = miroRecord,
        overrides = MiroSourceOverrides.empty,
        miroMetadata = MiroMetadata(isClearedForCatalogueAPI = true),
        version = 1
      )
      .isSuccess shouldBe false
}
