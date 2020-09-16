package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticApi._
import com.sksamuel.elastic4s.requests.searches.queries.MoreLikeThisQuery
import uk.ac.wellcome.platform.api.elasticsearch.ColorQuery.hexToRgb

class ColorQuery(binSizes: Seq[Int]) {

  def apply(field: String,
            hexColors: Seq[String],
            binIndices: Seq[Int] = binSizes.indices): MoreLikeThisQuery =
    moreLikeThisQuery(field)
      .likeTexts(getColorsSignature(hexColors.map(hexToRgb), binIndices))
      .copy(
        minTermFreq = Some(1),
        minDocFreq = Some(1),
        maxQueryTerms = Some(1000),
        minShouldMatch = Some("1")
      )

  private def getColorsSignature(colors: Seq[ColorQuery.Rgb],
                                 binIndices: Seq[Int]): Seq[String] =
    binIndices
      .map(binSizes)
      .flatMap { d =>
        val idx = componentIndex(d) _
        colors.map { color =>
          val indices = (idx(color._1), idx(color._2), idx(color._3))
          (indices._1 + d * indices._2 + d * d * indices._3, d)
        }
      }
      .map {
        case (index, d) => s"$index/$d"
      }

  private def componentIndex(nBins: Int)(i: Int): Int =
    math.floor(nBins * i / 256d).toInt

}

object ColorQuery {
  type Rgb = (Int, Int, Int)

  def hexToRgb(hex: String): Rgb = {
    val n = Integer.parseInt(hex, 16)
    (
      (n >> 16) & 0xFF,
      (n >> 8) & 0xFF,
      n & 0xFF
    )
  }
}
