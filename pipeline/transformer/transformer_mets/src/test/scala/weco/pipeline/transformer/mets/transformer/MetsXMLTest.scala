package weco.pipeline.transformer.mets.transformer

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources

class MetsXMLTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources {
  describe("failure conditions") {
    it("fails if the input string is not an xml") {
      MetsXml("hagdf") shouldBe a[Left[_, _]]
    }

    it("fails if the input string is not METS xml") {
      MetsXml("<x>hello</x>") shouldBe a[Left[_, _]]
    }
  }
  describe("choosing the right flavour of METS") {
    it("recognises a Goobi METS file") {
      val root = <mets:mets xmlns:mets="http://www.loc.gov/METS/">
        <mets:metsHdr CREATEDATE="2016-09-07T09:38:57">
          <mets:agent OTHERTYPE="SOFTWARE" ROLE="CREATOR" TYPE="OTHER">
            <mets:name>Goobi - ugh-3.0-ugh-2.0.0-24-gb21188e - 15−January−2016</mets:name>
            <mets:note>Goobi</mets:note>
          </mets:agent>
        </mets:metsHdr>
      </mets:mets>
      MetsXml(root) shouldBe GoobiMetsXml(root)
    }

    it("recognises an Archivematica METS file") {
      val root =
        <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:premis="http://www.loc.gov/premis/v3">
        <mets:amdSec ID="amdSec_1">
          <mets:digiprovMD ID="digiprovMD_5">
            <mets:mdWrap MDTYPE="PREMIS:AGENT">
              <mets:xmlData>
                <premis:agent version="3.0">
                  <premis:agentIdentifier>
                    <premis:agentIdentifierType>preservation system</premis:agentIdentifierType>
                    <premis:agentIdentifierValue>Archivematica-1.11</premis:agentIdentifierValue>
                  </premis:agentIdentifier>
                  <premis:agentName>Archivematica</premis:agentName>
                  <premis:agentType>software</premis:agentType>
                </premis:agent>
              </mets:xmlData>
            </mets:mdWrap>
          </mets:digiprovMD>
        </mets:amdSec>
      </mets:mets>
      MetsXml(root) shouldBe ArchivematicaMetsXML(root)
    }
  }

}
