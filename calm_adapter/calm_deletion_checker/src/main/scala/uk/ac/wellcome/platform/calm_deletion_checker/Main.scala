package uk.ac.wellcome.platform.calm_deletion_checker

import akka.Done
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp

import scala.concurrent.Future

object Main extends WellcomeTypesafeApp {
  runWithConfig { _ => () =>
    Future.successful(Done)
  }
}
