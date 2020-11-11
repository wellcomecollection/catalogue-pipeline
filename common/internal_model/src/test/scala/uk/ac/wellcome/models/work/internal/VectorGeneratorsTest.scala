package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.{VectorGenerators, VectorOps}

class VectorGeneratorsTest
    extends AnyFunSpec
    with VectorGenerators
    with Matchers {
  import VectorOps._

  val floatPrecision = 1e-5f

  it("generates random vectors of floats") {
    val vec = randomVector(2048, maxR = 10.0f)
    vec should have length 2048
    norm(vec) shouldBe <=(10.0f)
  }

  it("generates random vectors of strings") {
    val hash = randomHash(32)
    hash should have length 32
    every(hash) should fullyMatch regex "\\d+"
  }

  it("generates similar hashes") {
    val hashes = similarHashes(32, 5)
    val differences = hashes.map { hash =>
      (hashes.head.toSet -- hash.toSet).size
    }
    differences shouldBe sorted
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
