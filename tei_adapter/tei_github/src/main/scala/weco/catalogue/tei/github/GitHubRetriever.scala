package weco.catalogue.tei.github

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import uk.ac.wellcome.json.JsonUtil._

import java.net.URI
import scala.concurrent.Future

case class GitHubRetriever(gitHubRepoUrl: String, branch: String)(implicit ac: ActorSystem) {
  implicit val ec = ac.dispatcher

  def getFiles(window: Window): Future[List[URI]] = {
    val request = HttpRequest(uri = s"$gitHubRepoUrl/commits?since=${window.since.toString}&until=${window.until.toString}&sha=$branch")
    for {
      response <- Http().singleRequest(request)
      commits <- unmarshalAs[List[BaseCommit]](response)
      urls <- Future.sequence(commits.map(commit => getFilesChanged(commit)))
    } yield urls.flatten
  }

  private def unmarshalAs[T](response: HttpResponse)(implicit um: Unmarshaller[ResponseEntity, T]): Future[T] = {
    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[T]
      case _ => ???
    }
  }

  private def getFilesChanged(commit: BaseCommit): Future[List[URI]] = for {
    response <- Http().singleRequest(HttpRequest(uri = s"${gitHubRepoUrl}/commits/${commit.sha}"))
    fullCommit <- unmarshalAs[FullCommit](response)
  } yield fullCommit.files.map(file=> file.raw_url)
}

case class BaseCommit(sha: String)

case class FullCommit(sha: String, files: List[BBB])
case class BBB(raw_url: URI)
