package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.queries.{
  Query,
  SimpleQueryStringFlag,
  SimpleStringQuery
}

sealed trait WorkQuery {
  val queryString: String
  def query(): Query
}

object WorkQuery {
  val defaultMSM = "60%"
  val defaultBoostedFields = Seq(
    ("*.*", None),
    ("title", Some(9.0)),
    // Because subjects and genres have been indexed differently
    // We need to query them slightly differently
    // TODO: (jamesgorrie) think of a more sustainable way of doing this
    // maybe having a just a list of terms that we use terms queries to query against,
    // and then have more structured data underlying
    ("subjects.*", Some(8.0)),
    ("genres.label", Some(8.0)),
    ("description", Some(3.0)),
    ("contributors.*", Some(2.0))
  )

  case class MSMBoostQuery(queryString: String) extends WorkQuery {
    override def query(): SimpleStringQuery = {
      SimpleStringQuery(
        queryString,
        fields = defaultBoostedFields,
        lenient = Some(true),
        minimumShouldMatch = Some(defaultMSM),
        flags = Seq(SimpleQueryStringFlag.PHRASE)
      )
    }
  }
}
