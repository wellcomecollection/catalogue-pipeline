package weco.pipeline.transformer.sierra.transformers

import scala.util.Try
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.pipeline.transformer.sierra.SierraTransformer
import weco.pipeline.transformer.sierra.exceptions.SierraTransformerException

trait SierraTransformableTestBase extends Matchers {

  def transformToWork(transformable: SierraTransformable): Work[Source] = {
    val triedWork: Try[Work[Source]] =
      SierraTransformer(transformable, version = 1)

    if (triedWork.isFailure) {
      triedWork.failed.get.printStackTrace()
      println(
        triedWork.failed.get
          .asInstanceOf[SierraTransformerException]
          .e
          .getMessage
      )
    }

    triedWork.isSuccess shouldBe true
    triedWork.get
  }

  def assertTransformToWorkFails(transformable: SierraTransformable): Unit =
    SierraTransformer(transformable, version = 1).isSuccess shouldBe false
}
