package uk.ac.wellcome.models.parse

import org.scalatest.{FunSpec, Matchers}

import java.time.LocalDate

import uk.ac.wellcome.models.work.internal.InstantRange

class Marc008ParserTest extends FunSpec with Matchers {

  it("parses single date") {
    Marc008Parser("790922s1757    enk||||      o00||||eng ccam   ") shouldBe
      Some(InstantRange(LocalDate of (1757, 1, 1), LocalDate of (1757, 12, 31)))
  }

  it("parses partial date consisting of century") {
    Marc008Parser("081205s19uu    uk 015            vleng dngmuu ") shouldBe
      Some(InstantRange(LocalDate of (1900, 1, 1), LocalDate of (1999, 12, 31)))
  }

  it("parses partial date consisting of century and deccade") {
    Marc008Parser("090316s199u    sz                kuita dnkm1  ") shouldBe
      Some(InstantRange(LocalDate of (1990, 1, 1), LocalDate of (1999, 12, 31)))
  }

  it("parses multiple dates") {
    Marc008Parser("090914m16251700xx                  engddnteuua") shouldBe
      Some(InstantRange(LocalDate of (1625, 1, 1), LocalDate of (1700, 12, 31)))
  }

  it("parses reprint dates") {
    Marc008Parser("971101r19961925enk           000 | eng  namIa ") shouldBe
      Some(InstantRange(LocalDate of (1996, 1, 1), LocalDate of (1996, 12, 31)))
  }

  it("parses detailed dates") {
    Marc008Parser("030623e19081121ua                k0eng dnkm a ") shouldBe
      Some(
        InstantRange(LocalDate of (1908, 11, 21), LocalDate of (1908, 11, 21)))
  }

  it("parses publication and copyright dates") {
    Marc008Parser("770806t19071907nyuaf   sb    001 0 eng dcam a ") shouldBe
      Some(InstantRange(LocalDate of (1907, 1, 1), LocalDate of (1907, 12, 31)))
  }

  it("parses continuing resource ceased publication") {
    Marc008Parser("841217d19161924enkar         0    0eng dcasIa ") shouldBe
      Some(InstantRange(LocalDate of (1916, 1, 1), LocalDate of (1924, 12, 31)))
  }

  it("parses continuing resource currently published") {
    Marc008Parser("851018c20009999cauqr p  o    0   a0eng ccas2a ") shouldBe
      Some(InstantRange(LocalDate of (2000, 1, 1), LocalDate of (9999, 12, 31)))
  }

  it("parses continuing resource status unknown") {
    Marc008Parser("751101u1959uuuubl br p s     0   b0por ccas2a ") shouldBe
      Some(InstantRange(LocalDate of (1959, 1, 1), LocalDate of (9999, 12, 31)))
  }

  it("parses questionable dates") {
    Marc008Parser("030818q16001699                  kn    dnka a ") shouldBe
      Some(InstantRange(LocalDate of (1600, 1, 1), LocalDate of (1699, 12, 31)))
  }

  it("parses dates with different release and production") {
    Marc008Parser("090302p20082006enka    s     001 ideng dnac a ") shouldBe
      Some(InstantRange(LocalDate of (2008, 1, 1), LocalDate of (2008, 12, 31)))
  }

  it("parses single date when date2 filled with number sign") {
    Marc008Parser("051017s1874####||||||||s|||||||| ||eng#unam a ") shouldBe
      Some(InstantRange(LocalDate of (1874, 1, 1), LocalDate of (1874, 12, 31)))
  }

  it("parses single date when empty created date") {
    Marc008Parser("      s2003    enk050        0   vneng dngm a ") shouldBe
      Some(InstantRange(LocalDate of (2003, 1, 1), LocalDate of (2003, 12, 31)))
  }
}
