package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.ReferenceNumber

class TeiReferenceNumberTest extends AnyFunSpec with Matchers {
  it("returns None if it can't find a reference number") {
    val refNo = TeiReferenceNumber(
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_16046">
      </TEI>
    )

    refNo shouldBe None
  }

  it("finds a single reference number") {
    val refNo = TeiReferenceNumber(
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_16046">
        <teiHeader>
          <fileDesc>
            <publicationStmt>
              <idno>UkLW</idno>
              <idno type="msID">WMS_Arabic_404</idno>
              <idno type="catalogue">Fihrist</idno>
            </publicationStmt>
          </fileDesc>
        </teiHeader>
      </TEI>
    )

    refNo shouldBe Some(ReferenceNumber("WMS_Arabic_404"))
  }

  it("removes whitespace from the reference numbers") {
    val refNo = TeiReferenceNumber(
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_16047">
        <teiHeader>
          <fileDesc>
            <publicationStmt>
              <idno type="msID">WMS_Arabic_405 </idno>
            </publicationStmt>
          </fileDesc>
        </teiHeader>
      </TEI>
    )

    refNo shouldBe Some(ReferenceNumber("WMS_Arabic_405"))
  }

  it("returns None if the reference number is ambiguous") {
    val refNo = TeiReferenceNumber(
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_16200">
        <teiHeader>
          <fileDesc>
            <publicationStmt>
              <idno type="msID">WMS_Arabic_200</idno>
              <idno type="msID">WMS_Arabic_201</idno>
            </publicationStmt>
          </fileDesc>
        </teiHeader>
      </TEI>
    )

    refNo shouldBe None
  }
}
