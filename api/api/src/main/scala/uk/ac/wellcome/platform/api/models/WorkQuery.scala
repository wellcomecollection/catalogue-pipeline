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

  case class JustBoostQuery(queryString: String) extends WorkQuery {
    override def query(): MultiMatchQuery = {
      multiMatchQuery(queryString)
        .fields(
          "*",
          "subjects*^4",
          "genres*^4",
          "title^3")
        .matchType(MultiMatchQueryBuilderType.CROSS_FIELDS)
    }
  }

  case class BroaderBoostQuery(queryString: String) extends WorkQuery {
    override def query(): MultiMatchQuery = {
      multiMatchQuery(queryString)
        .fields(
          "*",
          "subjects*^8",
          "genres*^8",
          "title^5",
          "description*^2",
          "lettering*^2",
          "contributors*^2")
        .matchType(MultiMatchQueryBuilderType.CROSS_FIELDS)
    }
  }

  case class SlopQuery(queryString: String) extends WorkQuery {
    // TODO: 'phrase_match' rather than 'cross_fields' seems to reject fields("*") as used elsewhere, why?
    private val all_fields = List(
      "subjects",
      "genres",
      "title",
      "description",
      "lettering",
      "contributors"
    )
    override def query() = {
      multiMatchQuery(queryString)
        .fields(all_fields)
        .matchType(MultiMatchQueryBuilderType.PHRASE)
        .slop(3)
    }
  }

  case class MinimumMatchQuery(queryString: String) extends WorkQuery {
    override def query(): MultiMatchQuery = {
      multiMatchQuery(queryString)
        .fields("*")
        .matchType(MultiMatchQueryBuilderType.CROSS_FIELDS)
        .minimumShouldMatch("70%")
    }
  }
}
