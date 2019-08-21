package uk.ac.wellcome.platform.ingestor.models

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

sealed trait PipelineResult
case class PipelineSucceeded() extends PipelineResult
case class PipelineFailedDetermanistically() extends PipelineResult
case class PipelineFailedUndetermanistically() extends PipelineResult
case class PipelineGetError() extends PipelineResult

sealed trait PipelineStepState[+DataIn]
case class PipelineStepStarted extends PipelineStepState
case class PipelineStepWorking[DataIn] extends PipelineStepState
case class PipelineStepStopped[DataIn, PipelineDetermanisticError]
    extends PipelineStepState
case class PipelineStepSucceeded[DataIn, DataOut] extends PipelineStepState
case class PipelineStepFailed[DataIn, PipelineUndetermanisticError]
    extends PipelineStepState

trait PipelineStepGetter[DataType] {
  def get(): Try[Future[DataType]]
}

trait PipelineStep[DataIn, DataOut] {
  def get(): Try[Future[DataIn]]
  def runProcess() =
    get match {
      case Failure(e) => PipelineGetError(e)
      case Success(f) => f map process
    }

  def process(data: DataIn): Try[Future[DataOut]]
}

trait PipelineLogger

object BigMessaging extends PipelineStepGetter[CatalogueWork] {
  def get(): Try[Future[CatalogueWork]] = {
    // Get work from BM
    Success(Future.successful(CatalogueWork("Title")))
  }
}

case class ElasticResult(msg: String)
case class CatalogueWork(title: String)

class IngestPipelineStep extends PipelineStep[CatalogueWork, ElasticResult] {

  def get() = {
    BigMessaging.get()
  }
  def process(work: CatalogueWork) = {
    Success(Future.successful(ElasticResult("Made it!")))
  }
}

object App {
  def run(): Unit = {
    new IngestPipelineStep()
  }
}
