package uk.ac.wellcome.calm_adapter

// import akka.http.scaladsl.Http

trait CalmRetriever {

  def getRecords(query: CalmQuery): Either[Throwable, List[CalmRecord]]
}

case class CalmCredentials(username: String, password: String)

class HttpCalmRetriever(url: String, credentials: CalmCredentials) {

  def getRecords(query: CalmQuery): Either[Throwable, List[CalmRecord]] = ???
}
