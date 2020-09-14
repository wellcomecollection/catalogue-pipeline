package uk.ac.wellcome.platform.api.elasticsearch

class ColorQuery(binSizes: Seq[Int], repetitions: Int) {

  def getColorSignature(color: ColorQuery.Rgb): Seq[String] =
    binSizes
      .flatMap { nBins =>
        val idx = componentIndex(nBins) _
        val indices = (idx(color._1), idx(color._2), idx(color._3))
        val d = nBins - 1
        Seq.fill(repetitions)(
          (indices._1 + d * indices._2 + d * d * indices._3, nBins)
        )
      }
      .map {
        case (index, nBins) => s"${index}/${nBins}"
      }

  private def componentIndex(nBins: Int)(i: Int): Int =
    math.floor(nBins * i / 256d).toInt

}

object ColorQuery {
  type Rgb = (Int, Int, Int)

  def getSignatureParams(sample: Seq[String]): (Seq[Int], Int) = {
    val binSizes = sample.map(_.split("/").last.toInt).distinct
    val repetitions = sample.count(_.endsWith(binSizes.head.toString))
    (binSizes, repetitions)
  }

  def hexToRgb(hex: String): Rgb = {
    val n = Integer.parseInt(hex, 16)
    (
      (n >> 16) & 0xFF,
      (n >> 8) & 0xFF,
      n & 0xFF
    )
  }
}
