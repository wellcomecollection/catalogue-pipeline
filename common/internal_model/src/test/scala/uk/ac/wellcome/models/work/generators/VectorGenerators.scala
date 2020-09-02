package uk.ac.wellcome.models.work.generators

import scala.util.Random

trait VectorGenerators {
  import VectorOps._

  private val defaultSimilarity = math.cos(Math.PI / 64).toFloat

  lazy val simHasher4096 = new SimHasher(4096, bins = (256, 256))

  def randomVector(d: Int, maxR: Float = 1.0f): Vec = {
    val rand = normalize(randomUniform(d))
    val r = maxR * math.pow(Random.nextFloat(), 1.0 / d).toFloat
    scalarMultiply(r, rand)
  }

  def subspaceSimilarVector(a: Vec,
                            similarity: Float = defaultSimilarity,
                            subspaces: Int = 256): Vec =
    a.grouped(a.size / subspaces)
      .flatMap(similarVector(_, similarity))
      .toSeq

  def similarVector(a: Vec, similarity: Float = defaultSimilarity): Vec = {
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
}

/*
 * This implements a modified version of the SimHash algorithm,
 * splitting vectors into subspaces before applying the hashing
 * and encoding the resultant signatures into integers.
 *
 * The original (unmodified) algorithm was taken from these slides:
 * http://www.cs.jhu.edu/~vandurme/papers/VanDurmeLallACL10-slides.pdf
 */
class SimHasher(d: Int, bins: (Int, Int) = (256, 256)) {
  import VectorOps._

  private val groupSize = d / bins._1
  private val hashSize = math.ceil(log2(bins._2)).toInt
  lazy private val projections = createMatrix(hashSize, groupSize)(
    Random.nextGaussian().toFloat / hashSize
  )

  def lsh(vec: Vec): Seq[String] = {
    assert(vec.size == d)
    vec
      .grouped(groupSize)
      .zipWithIndex
      .map {
        case (group, index) =>
          val hash = projections.zipWithIndex.foldLeft(0) {
            case (s, (row, i)) if dot(group, row) >= 0 => s | 1 << i
            case (s, _)                                => s
          }
          s"$index-$hash"
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

  def createMatrix(m: Int, n: Int)(value: => Float): Seq[Vec] =
    Seq.fill(m)(Seq.fill(n)(value))

  def log2(x: Float): Float =
    (math.log(x) / math.log(2)).toFloat

  def dot(a: Vec, b: Vec): Float =
    (a zip b).map(Function.tupled(_ * _)).sum

  def randomNormal(d: Int): Vec = Seq.fill(d)(Random.nextGaussian().toFloat)

  def randomUniform(d: Int): Vec = Seq.fill(d)(Random.nextFloat)
}
