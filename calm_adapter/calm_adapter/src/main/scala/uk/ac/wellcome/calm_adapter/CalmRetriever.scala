package uk.ac.wellcome.calm_adapter

import java.time.LocalDate

case class CalmRecord(data: Map[String, String])

sealed trait CalmQuery

object CalmQuery {

  case class ModifiedDate(date: LocalDate) extends CalmQuery
}

trait CalmRetriever {

  def getRecords(query: CalmQuery): Either[Throwable, List[CalmRecord]]
}

case class  HttpCalmCredentials(username: String, password: String)

class HttpCalmRetriever(url: String, credentials: HttpCalmCredentials) {

  def getRecords(query: CalmQuery): Either[Throwable, List[CalmRecord]] = ???
}
