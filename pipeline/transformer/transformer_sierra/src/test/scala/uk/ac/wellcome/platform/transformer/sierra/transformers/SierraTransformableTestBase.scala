package uk.ac.wellcome.platform.transformer.sierra.transformers

import scala.util.Try
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformableTransformer
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import WorkState.Unidentified

trait SierraTransformableTestBase extends Matchers {

  def transformToWork(
    transformable: SierraTransformable): Work[Unidentified] = {
    val triedWork: Try[Work[Unidentified]] =
      SierraTransformableTransformer(transformable, version = 1)

    if (triedWork.isFailure) {
      triedWork.failed.get.printStackTrace()
      println(
        triedWork.failed.get
          .asInstanceOf[SierraTransformerException]
          .e
          .getMessage)
    }

    triedWork.isSuccess shouldBe true
    triedWork.get
  }

  def assertTransformToWorkFails(transformable: SierraTransformable): Unit = {
    SierraTransformableTransformer(transformable, version = 1).isSuccess shouldBe false
  }
}
