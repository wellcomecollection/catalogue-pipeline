package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.model.Uri
import uk.ac.wellcome.platform.api.models.{ResultList, SearchOptions}

trait Paginated { this: QueryParams =>
  val page: Option[Int]
  val pageSize: Option[Int]

  def paginationErrors: List[String] =
    List(
      page
        .filterNot(_ >= 1)
        .map(_ => "page: must be greater than 1"),
      pageSize
        .filterNot(size => size >= 1 && size <= 100)
        .map(_ => "pageSize: must be between 1 and 100")
    ).flatten

}

trait PaginatedSearchOptions {
  val pageSize: Int
  val pageNumber: Int
}

case class PaginationResponse(
  totalPages: Int,
  prevPage: Option[String],
  nextPage: Option[String]
)

object PaginationResponse {
  def apply(resultList: ResultList[_, _],
            searchOptions: PaginatedSearchOptions,
            requestUri: Uri): PaginationResponse = {
    val totalPages =
      getTotalPages(resultList.totalResults, searchOptions.pageSize)
    PaginationResponse(
      totalPages = totalPages,
      prevPage = pageLink(searchOptions.pageNumber - 1, totalPages, requestUri),
      nextPage = pageLink(searchOptions.pageNumber + 1, totalPages, requestUri)
    )
  }

  private def pageLink(page: Int,
                       totalPages: Int,
                       requestUri: Uri): Option[String] =
    if (pageInBounds(page, totalPages))
      Some(
        requestUri
          .withQuery(
            pageQuery(page, requestUri.query())
          )
          .toString
      )
    else
      None

  private def pageQuery(page: Int, previousQuery: Uri.Query) =
    Uri.Query(
      previousQuery.toMap.updated("page", page.toString)
    )

  private def pageInBounds(page: Int, totalPages: Int) =
    page > 0 && page <= totalPages

  private def getTotalPages(totalResults: Int, pageSize: Int): Int =
    Math.ceil(totalResults.toDouble / pageSize).toInt
}

object PaginationQuery {
  def safeGetFrom(searchOptions: SearchOptions[_]): Int = {
    // Because we use Int for the pageSize and pageNumber, computing
    //
    //     from = (pageNumber - 1) * pageSize
    //
    // can potentially overflow and be negative or even wrap around.
    // For example, pageNumber=2018634700 and pageSize=100 would return
    // results if you don't handle this!
    //
    // If we are about to overflow, we pass the max possible int
    // into the Elasticsearch query and let it bubble up from there.
    // We could skip the query and throw here, because the user is almost
    // certainly doing something wrong, but that would mean simulating an
    // ES error or modifying our exception handling, and that seems more
    // disruptive than just clamping the overflow.
    //
    // Note: the checks on "pageSize" mean we know it must be
    // at most 100.

    // How this check works:
    //
    //    1.  If pageNumber > MaxValue, then (pageNumber - 1) * pageSize is
    //        probably bigger, as pageSize >= 1.
    //
    //    2.  Alternatively, we care about whether
    //
    //            pageSize * pageNumber > MaxValue
    //
    //        Since pageNumber is known positive, this is equivalent to
    //
    //            pageSize > MaxValue / pageNumber
    //
    //        And we know the division won't overflow because we have
    //        pageValue < MaxValue by the first check.
    //
    val willOverflow =
      (searchOptions.pageNumber > Int.MaxValue) ||
        (searchOptions.pageSize > Int.MaxValue / searchOptions.pageNumber)

    val from = if (willOverflow) {
      Int.MaxValue
    } else {
      (searchOptions.pageNumber - 1) * searchOptions.pageSize
    }

    assert(
      from >= 0,
      message = s"from = $from < 0, which is daft.  Has something overflowed?"
    )

    from
  }
}
