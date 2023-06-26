package weco.catalogue.internal_model.generators

import weco.fixtures.RandomGenerators

trait VectorGenerators extends RandomGenerators {
  import VectorOps._

  def randomVector(d: Int, maxR: Float = 1.0f): Vec = {
    val rand = normalize(randomNormal(d))
    val r = maxR * math.pow(random.nextFloat(), 1.0 / d).toFloat
    scalarMultiply(r, rand)
  }

  def randomUnitLengthVector(d: Int): Vec = normalize(randomNormal(d))

  private def randomNormal(d: Int): Vec =
    Seq.fill(d)(random.nextGaussian().toFloat)
}

object VectorOps {
  type Vec = Seq[Float]

  def norm(vec: Vec): Float =
    math.sqrt(vec.fold(0.0f)((total, i) => total + (i * i))).toFloat

  def normalize(vec: Vec): Vec =
    scalarMultiply(1 / norm(vec), vec)

  def scalarMultiply(a: Float, vec: Vec): Vec = vec.map(_ * a)
}
