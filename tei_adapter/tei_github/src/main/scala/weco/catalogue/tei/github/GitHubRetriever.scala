package weco.catalogue.tei.github

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}

import java.net.URL
import scala.concurrent.Future


case class GitHubRetriever(gitHubRepoUrl: String, branch: String)(implicit ac: ActorSystem) {
  def getFiles(window: Window): Future[List[URL]] = {
    for {
      response <- Http().singleRequest(HttpRequest(uri = s"${gitHubRepoUrl}commits?since=${window.since.toString}&until=${window.until.toString}&branch=$branch"))
      _ <- response.
    } yield(List())
  }
}
