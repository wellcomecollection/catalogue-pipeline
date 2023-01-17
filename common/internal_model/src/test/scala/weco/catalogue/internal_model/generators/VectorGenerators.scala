package weco.catalogue.internal_model.generators

import weco.fixtures.RandomGenerators

trait VectorGenerators extends RandomGenerators {
  import VectorOps._

  private val defaultSimilarity = math.cos(Math.PI / 64).toFloat

  def randomColorVector(binSizes: Seq[Seq[Int]] =
                          Seq(Seq(4, 6, 9), Seq(2, 4, 6), Seq(1, 3, 5)),
                        nTokens: Int = 100): Seq[String] =
    binSizes.transpose.zipWithIndex.flatMap {
      case (bins, binIndex) =>
        List.fill(nTokens / binSizes.size) {
          val maxIndex = bins(1) + (bins(0) * bins(1) * bins(2))
          val c = random.nextInt(maxIndex)
          s"$c/$binIndex"
        }
    }

  def similarColorVectors(n: Int,
                          binSizes: Seq[Seq[Int]] =
                            Seq(Seq(4, 6, 9), Seq(2, 4, 6), Seq(1, 3, 5)),
                          nTokens: Int = 100): Seq[Seq[String]] = {
    val baseIndices = binSizes.transpose.zipWithIndex.flatMap {
      case (bins, binIndex) =>
        val maxIndex = bins(1) + (bins(0) * bins(1) * bins(2))
        Seq.fill(nTokens / binSizes.size)(
          (random.nextInt(maxIndex), maxIndex, binIndex))
    }
    (0 until n)
      .map { i =>
        random.shuffle(Seq.fill(nTokens - i)(0).padTo(nTokens, 1))
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
    val r = maxR * math.pow(random.nextFloat(), 1.0 / d).toFloat
    scalarMultiply(r, rand)
  }

  def randomUnitLengthVector(d: Int): Vec = normalize(randomNormal(d))

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

  private def randomNormal(d: Int): Vec =
    Seq.fill(d)(random.nextGaussian().toFloat)
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

  def proj(a: Vec, b: Vec): Vec =
    scalarMultiply(dot(a, b) / dot(a, a), a)
}
