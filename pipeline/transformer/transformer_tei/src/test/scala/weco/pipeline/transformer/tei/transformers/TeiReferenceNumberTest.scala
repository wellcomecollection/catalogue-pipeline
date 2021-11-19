package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.ReferenceNumber
import weco.pipeline.transformer.tei.generators.TeiGenerators

class TeiReferenceNumberTest
    extends AnyFunSpec
    with Matchers
    with EitherValues with TeiGenerators {
  it("returns a Left[error] if it can't find a reference number") {
    val err = TeiReferenceNumber(
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_16046">
      </TEI>
    )

    err.left.value.getMessage shouldBe "No <idno type='msID'> found!"
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

    refNo.value shouldBe ReferenceNumber("WMS_Arabic_404")
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

    refNo.value shouldBe ReferenceNumber("WMS_Arabic_405")
  }

  it("fails if the reference number is ambiguous") {
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

    refNo.left.value.getMessage should startWith(
      "Multiple instances of <idno type='msID'> found!")
  }
  it("fails if the reference number is empty") {
    val result =
      TeiReferenceNumber(teiXml(id = "id", refNo = idnoMsId("")))

    result shouldBe a[Left[_, _]]
    result.left.value.getMessage should startWith("Empty <idno type='msID'>")
  }
}
