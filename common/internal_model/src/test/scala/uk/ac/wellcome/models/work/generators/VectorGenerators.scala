package uk.ac.wellcome.models.work.generators

import scala.util.Random

trait VectorGenerators {
  def randomVector(d: Int, maxR: Float = 1.0f): Seq[Float] = {
    val rand = Seq.fill(d)(Random.nextFloat)
    val r = math.pow(Random.nextFloat() * maxR, 1.0 / d).toFloat
    val denom = norm(rand)
    rand.map(r * _ / denom)
  }

  def nearbyVector(a: Seq[Float], epsilon: Float = 0.1f): Seq[Float] =
    (a zip randomVectorOnBall(a.size, epsilon)).map {
      case (x, e) => x + e
    }

  def norm(vec: Seq[Float]): Float =
    math.sqrt(vec.fold(0.0f)(_ + math.pow(_, 2).toFloat)).toFloat

  class BinHasher(d: Int, bins: (Int, Int) = (256, 256)) {
    private val groupSize = d / bins._1
    private val hashSize = (math.log(bins._2) / math.log(2.0f)).toInt
    lazy private val projections =
      Seq.fill(hashSize)(Seq.fill(groupSize)(Random.nextGaussian() / hashSize))

    def lsh(vec: Seq[Float]): Seq[String] = {
      assert(vec.size == d)
      vec
        .grouped(groupSize)
        .zipWithIndex
        .map {
          case (group, index) =>
            val bits = projections.foldLeft("") {
              case (str, row)
                  if (group zip row).map(Function.tupled(_ * _)).sum >= 0 =>
                str + "1"
              case (str, _) => str + "0"
            }
            val hash = Integer.parseInt(bits, 2)
            s"$index-$hash"
        }
        .toSeq
    }
  }

  private def randomVectorOnBall(d: Int, r: Float): Seq[Float] = {
    val randVec = Seq.fill(d)(Random.nextGaussian().toFloat)
    val denom = norm(randVec)
    randVec.map(r * _ / denom)
  }
}
