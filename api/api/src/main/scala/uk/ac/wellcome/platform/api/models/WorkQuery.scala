package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.queries.{
  Query,
  SimpleStringQuery
}

sealed trait WorkQuery {
  val queryString: String
  def query(): Query
}

object WorkQuery {
  val defaultMSM = "60%"
  val defaultBoostedFields = Seq(
    ("*", Some(1.0)),
    ("title", Some(9.0)),
    ("subjects*", Some(8.0)),
    ("genres*", Some(8.0)),
    ("description*", Some(5.0)),
    ("contributors*", Some(2.0))
  )

  case class SimpleQuery(queryString: String) extends WorkQuery {
    override def query(): SimpleStringQuery = {
      simpleStringQuery(queryString)
    }
  }

  case class MSMQuery(queryString: String) extends WorkQuery {
    override def query(): SimpleStringQuery = {
      SimpleStringQuery(
        queryString,
        fields = Seq(("*", Some(1.0))),
        minimumShouldMatch = Some(defaultMSM))
    }
  }

  case class BoostQuery(queryString: String) extends WorkQuery {
    override def query(): SimpleStringQuery = {
      SimpleStringQuery(
        queryString,
        fields = defaultBoostedFields,
        lenient = Some(true)
      )
    }
  }

  case class MSMBoostQuery(queryString: String) extends WorkQuery {
    override def query(): SimpleStringQuery = {
      SimpleStringQuery(
        queryString,
        fields = defaultBoostedFields,
        lenient = Some(true),
        minimumShouldMatch = Some(defaultMSM)
      )
    }
  }
}
