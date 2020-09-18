package uk.ac.wellcome.models.work.generators

import scala.util.Random

trait VectorGenerators {
  import VectorOps._

  private val defaultSimilarity = math.cos(Math.PI / 64).toFloat

  lazy val simHasher4096 = new SimHasher(4096, bins = (256, 128))

  def randomColorVector(binSizes: Seq[Int] = Seq(4, 6, 8),
                        weights: Seq[Int] = Seq(2, 2, 1, 1, 1)): Seq[String] =
    binSizes.flatMap { binSize =>
      weights.flatMap { weight =>
        val maxIndex = (math.pow(binSize, 3) - 1).toInt
        val c = Random.nextInt(maxIndex)
        Seq.fill(weight)(s"$c/$binSize")
      }
    }

  def similarColorVectors(
    n: Int,
    binSizes: Seq[Int] = Seq(4, 6, 8),
    weights: Seq[Int] = Seq(2, 2, 1, 1, 1)): Seq[Seq[String]] = {
    val baseIndices = binSizes.flatMap { binSize =>
      val maxIndex = (math.pow(binSize, 3) - 1).toInt
      weights.flatMap { weight =>
        Seq.fill(weight)((Random.nextInt(maxIndex), maxIndex, binSize))
      }
    }
    val nElements = binSizes.size * weights.sum
    (0 until n)
      .map { i =>
        Random.shuffle(Seq.fill(nElements - i)(0).padTo(nElements, 1))
      }
      .map { offsets =>
        (baseIndices zip offsets).map {
          case ((base, max, binSize), offset) =>
            s"${(base + offset) % max}/$binSize"
        }
      }
  }

  def randomVector(d: Int, maxR: Float = 1.0f): Vec = {
    val rand = normalize(randomNormal(d))
    val r = maxR * math.pow(Random.nextFloat(), 1.0 / d).toFloat
    scalarMultiply(r, rand)
  }

  def cosineSimilarVector(a: Vec,
                          similarity: Float = defaultSimilarity): Vec = {
    val r = norm(a)
    val unitA = normalize(a)
    val rand = normalize(randomNormal(a.size))
    val perp = normalize(
      add(
        rand,
        scalarMultiply(-dot(rand, unitA), unitA)
      )
    )
    add(
      scalarMultiply(r * similarity, unitA),
      scalarMultiply(r * math.sqrt(1 - (similarity * similarity)).toFloat, perp)
    )
  }

  def nearbyVector(a: Vec, epsilon: Float = 0.1f): Vec =
    add(a, scalarMultiply(epsilon, normalize(randomNormal(a.size))))

  def similarVectors(d: Int, n: Int): Seq[Vec] = {
    val baseVec = randomVector(d, maxR = n.toFloat)
    val direction = randomVector(d)
    val otherVecs = (1 until n).map { i =>
      add(baseVec, scalarMultiply(i / 10f, direction))
    }
    baseVec +: otherVecs
  }
}

/*
 * This implements a modified version of the SimHash algorithm,
 * splitting vectors into subspaces before applying the hashing
 * and encoding the resultant signatures into integers.
 *
 * The original (unmodified) algorithm was taken from these slides:
 * http://www.cs.jhu.edu/~vandurme/papers/VanDurmeLallACL10-slides.pdf
 */
class SimHasher(d: Int, bins: (Int, Int) = (256, 64)) {
  import VectorOps._

  private val hashSize = log2(bins._2).toInt * (bins._1 - 1)
  lazy private val projections = (
    createMatrix(hashSize, d)(Random.nextGaussian().toFloat)
  )

  def lsh(vec: Vec): Seq[String] = {
    assert(vec.size == d)
    projections.zipWithIndex
      .foldLeft(BigInt(0)) {
        case (s, (row, i)) if dot(vec, row) >= 0 => s.setBit(i)
        case (s, (_, i))                         => s.clearBit(i)
      }
      .toString(2)
      .padTo(hashSize, "0")
      .mkString
      .grouped(hashSize / bins._1)
      .zipWithIndex
      .map {
        case (str, i) => s"$i-${BigInt(str, 2).toString(10)}"
      }
      .toSeq
  }
}

object VectorOps {
  type Vec = Seq[Float]

  def norm(vec: Vec): Float =
    math.sqrt(vec.fold(0.0f)((total, i) => total + (i * i))).toFloat

  def normalize(vec: Vec): Vec =
    scalarMultiply(1 / norm(vec), vec)

  def euclideanDistance(a: Vec, b: Vec): Float =
    norm(add(a, scalarMultiply(-1, b)))

  def cosineSimilarity(a: Vec, b: Vec): Float =
    dot(a, b) / (norm(a) * norm(b))

  def scalarMultiply(a: Float, vec: Vec): Vec = vec.map(_ * a)

  def add(a: Vec, b: Vec): Vec =
    (a zip b).map(Function.tupled(_ + _))

  def sub(a: Vec, b: Vec): Vec =
    (a zip b).map(Function.tupled(_ - _))

  def createMatrix(m: Int, n: Int)(value: => Float): Seq[Vec] =
    Seq.fill(m)(Seq.fill(n)(value))

  def log2(x: Float): Float =
    (math.log(x) / math.log(2)).toFloat

  def dot(a: Vec, b: Vec): Float =
    (a zip b).map(Function.tupled(_ * _)).sum

  def randomNormal(d: Int): Vec = Seq.fill(d)(Random.nextGaussian().toFloat)

  def randomUniform(d: Int): Vec = Seq.fill(d)(Random.nextFloat)

  def proj(a: Vec, b: Vec): Vec =
    scalarMultiply(dot(a, b) / dot(a, a), a)
}
