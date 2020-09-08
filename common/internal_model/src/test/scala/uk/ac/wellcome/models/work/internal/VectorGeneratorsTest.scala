package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.{
  SimHasher,
  VectorGenerators,
  VectorOps
}

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

  describe("SimHasher") {
    val d = 4096
    val simHasher = new SimHasher(d)

    it("deterministically hashes vectors") {
      val vec = randomVector(d)
      val hash1 = simHasher.lsh(vec)
      val hash2 = simHasher.lsh(vec)

      hash1 should equal(hash2)
    }

    it("outputs similar hashes for similar vectors") {
      val vecA = randomVector(d, maxR = 10.0f)
      val vecB =
        cosineSimilarVector(vecA, similarity = math.cos(Math.PI / 64).toFloat)
      val hashA = simHasher.lsh(vecA)
      val hashB = simHasher.lsh(vecB)

      val difference = hashA.toSet diff hashB.toSet
      difference.size should be <= (0.25 * hashA.size).toInt
    }

    it("outputs differing hashes for dissimilar vectors") {
      val vecA = randomVector(d, maxR = 10.0f)
      val vecB =
        cosineSimilarVector(vecA, similarity = math.cos(Math.PI / 2).toFloat)
      val hashA = simHasher.lsh(vecA)
      val hashB = simHasher.lsh(vecB)

      val difference = hashA diff hashB
      difference.size should be >= (0.75 * hashA.size).toInt
    }

    it("preserves ordering of similarities") {
      val vecs = similarVectors(d, 10)
      val hash = simHasher.lsh(vecs.head)
      val otherHashes = vecs.tail.map(simHasher.lsh)
      val diffSizes = otherHashes.map(_ diff hash).map(_.size)

      diffSizes shouldBe sorted
    }
  }
}
