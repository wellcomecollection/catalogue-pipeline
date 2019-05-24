package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.{Query, SimpleStringQuery}
import com.sksamuel.elastic4s.searches.queries.matches.{
  MultiMatchQuery,
  MultiMatchQueryBuilderType
}

sealed trait WorkQuery {
  val queryString: String
  def query(): Query
}

object WorkQuery {

  case class SimpleQuery(queryString: String) extends WorkQuery {
    override def query(): SimpleStringQuery = {
      simpleStringQuery(queryString)
    }
  }

  case class MSMQuery(queryString: String) extends WorkQuery {
    override def query(): MultiMatchQuery = {
      multiMatchQuery(queryString)
        .fields("*")
        .matchType(MultiMatchQueryBuilderType.CROSS_FIELDS)
        .minimumShouldMatch("70%")
    }
  }

  case class BoostQuery(queryString: String) extends WorkQuery {
    override def query(): MultiMatchQuery = {
      multiMatchQuery(queryString)
        .fields(
          "*",
          "title^9",
          "subjects*^8",
          "genres*^8",
          "description*^5",
          "contributors*^2")
        .matchType(MultiMatchQueryBuilderType.CROSS_FIELDS)
    }
  }

  case class MSMBoostQuery(queryString: String) extends WorkQuery {
    override def query(): MultiMatchQuery = {
      multiMatchQuery(queryString)
        .fields(
          "*",
          "title^9",
          "subjects*^8",
          "genres*^8",
          "description*^5",
          "contributors*^2")
        .matchType(MultiMatchQueryBuilderType.CROSS_FIELDS)
        .minimumShouldMatch("70%")
    }
  }
}
