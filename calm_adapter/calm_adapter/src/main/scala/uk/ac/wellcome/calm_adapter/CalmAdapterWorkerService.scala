package uk.ac.wellcome.calm_adapter

import scala.concurrent.Future
import akka.Done
import grizzled.slf4j.Logging

import uk.ac.wellcome.typesafe.Runnable

class CalmAdapterWorkerService() extends Runnable with Logging {

  def run(): Future[Done] = Future.successful(Done)
}
