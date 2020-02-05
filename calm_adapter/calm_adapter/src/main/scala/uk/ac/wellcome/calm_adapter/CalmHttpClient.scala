package uk.ac.wellcome.calm_adapter

import akka.actor.ActorSystem
import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._

trait CalmHttpClient {

  def apply(request: HttpRequest): Future[HttpResponse]
}

class CalmAkkaHttpClient(implicit actorSystem: ActorSystem)
    extends CalmHttpClient {

  def apply(request: HttpRequest): Future[HttpResponse] =
    Http().singleRequest(request)
}
