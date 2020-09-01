package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.VectorGenerators

class VectorGeneratorsTest
    extends AnyFunSpec
    with VectorGenerators
    with Matchers {

  it("generates random vectors of floats") {
    val vec = randomVector(2048, maxR = 10.0f)
    vec should have length 2048
    math.sqrt(vec.map(math.pow(_, 2)).sum) should be <= 10.0
  }

  it("generates vectors close to known vectors") {
    val vecA = randomVector(16)
    val vecB = nearbyVector(vecA, 3f)

    vecB should have length vecA.length
    vecB should not equal vecA
    val distance = math.sqrt((vecA zip vecB).map {
      case (a, b) => math.pow(a - b, 2)
    }.sum)
    (distance - 3f) should be <= 1e-5
  }

  describe("BinHasher") {
    val d = 4096
    val binHasher = new BinHasher(d)

    it("deterministically hashes vectors") {
      val vec = randomVector(d)
      val hash1 = binHasher.lsh(vec)
      val hash2 = binHasher.lsh(vec)

      hash1 should equal(hash2)
    }

    it("outputs similar hashes for nearby vectors") {
      val vecA = randomVector(d)
      val vecB = nearbyVector(vecA, epsilon = 0.01f * norm(vecA))
      val hashA = binHasher.lsh(vecA)
      val hashB = binHasher.lsh(vecB)

      val difference = hashA.toSet diff hashB.toSet
      difference.size should be <= (0.2 * hashA.size).toInt
    }

    it("outputs differing hashes for distant vectors") {
      val vecA = randomVector(d)
      val vecB = nearbyVector(vecA, 64f)
      val hashA = binHasher.lsh(vecA)
      val hashB = binHasher.lsh(vecB)

      val difference = hashA diff hashB
      difference.size should be >= (0.8 * hashA.size).toInt
    }

    it("preserves ordering of distances") {
      val vec = randomVector(d, maxR = 10.0f)
      val direction = randomVector(d)
      val otherVecs = (1 to 9).map { i =>
        (vec zip direction).map(Function.tupled(_ + i * _ / 10))
      }
      val hash = binHasher.lsh(vec)
      val otherHashes = otherVecs.map(binHasher.lsh)
      val diffSizes = otherHashes.map(_ diff hash).map(_.size)

      diffSizes shouldBe sorted
    }

  }
}
