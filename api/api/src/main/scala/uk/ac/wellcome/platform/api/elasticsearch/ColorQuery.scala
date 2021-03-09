package uk.ac.wellcome.platform.api.elasticsearch

import java.awt.Color

import com.sksamuel.elastic4s.ElasticApi._
import com.sksamuel.elastic4s.requests.searches.queries.MoreLikeThisQuery

class ColorQuery(binSizes: Seq[Seq[Int]], binMinima: Seq[Float]) {
  require(
    binSizes.size == 3 && binSizes.forall(_.size == 3),
    "binSizes must be a 3x3 sequence of ints"
  )
  require(
    binMinima.size == 3,
    "binMinima must be a sequence of 3 floats"
  )

  private final val minBinWidth = 1f / 256

  lazy private val transposedSizes = binSizes.transpose
  lazy private val satMin = binMinima(1)
  lazy private val valMin = binMinima(2)

  def apply(field: String,
            hexColors: Seq[String],
            binIndices: Seq[Int] = binSizes.indices): MoreLikeThisQuery =
    moreLikeThisQuery(field)
      .likeTexts(
        getColorsSignature(hexColors.map(ColorQuery.hexToHsv), binIndices))
      .copy(
        minTermFreq = Some(1),
        minDocFreq = Some(1),
        maxQueryTerms = Some(1000),
        minShouldMatch = Some("1")
      )
      .boost(1000)

  // This replicates the logic in palette_encoder.py:get_bin_index
  private def getColorsSignature(colors: Seq[ColorQuery.Hsv],
                                 binIndices: Seq[Int]): Seq[String] =
    binIndices
      .map(transposedSizes)
      .zip(binIndices)
      .flatMap {
        case (nBins, i) =>
          val nValBins = nBins(1)
          colors
            .map {
              case (_, _, v) if v < valMin => 0
              case (_, s, v) if s < satMin =>
                1 + math
                  .floor(nValBins * (v - valMin) / (1 - valMin + minBinWidth))
                  .toInt
              case (h, s, v) =>
                def idx(x: Float, i: Int): Int = {
                  val num = nBins(i) * (x - binMinima(i))
                  val denom = 1 - binMinima(i) + minBinWidth
                  math.floor(num / denom).toInt
                }
                1 + nValBins +
                  idx(h, 0) +
                  nBins(0) * idx(s, 1) +
                  nBins(0) * nBins(1) * idx(v, 2)
            }
            .map((_, i))
      }
      .map {
        case (binIndex, i) => s"$binIndex/$i"
      }

}

object ColorQuery {
  type Hsv = (Float, Float, Float)

  def hexToHsv(hex: String): Hsv = {
    val n = Integer.parseInt(hex, 16)
    val (r, g, b) = (
      (n >> 16) & 0xFF,
      (n >> 8) & 0xFF,
      n & 0xFF
    )
    val hsv = Color.RGBtoHSB(r, g, b, null)
    (hsv(0), hsv(1), hsv(2))
  }
}
