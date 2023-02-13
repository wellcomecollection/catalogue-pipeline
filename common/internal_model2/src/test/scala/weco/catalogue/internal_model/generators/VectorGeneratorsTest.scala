package weco.catalogue.internal_model.generators

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class VectorGeneratorsTest
    extends AnyFunSpec
    with VectorGenerators
    with Matchers {
  import weco.catalogue.internal_model.generators.VectorOps._

  val floatPrecision = 1e-5f

  it("generates random vectors of floats") {
    val vec = randomVector(2048, maxR = 10.0f)
    vec should have length 2048
    norm(vec) shouldBe <=(10.0f)
  }

  it("generates vectors close to known vectors") {
    val vecA = randomVector(16)
    val vecB = nearbyVector(vecA, 3f)

    vecB should have length vecA.length
    vecB should not equal vecA
    euclideanDistance(vecA, vecB) should be(3f +- floatPrecision)
  }

  it("generates vectors cosine-similar to known vectors") {
    val similarity = math.cos(Math.PI.toFloat / 6).toFloat
    val vecA = randomVector(16)
    val vecB = cosineSimilarVector(vecA, similarity)

    vecB should have length vecA.length
    vecB should not equal vecA
    similarity should be(cosineSimilarity(vecA, vecB) +- floatPrecision)
  }
}
