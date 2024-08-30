package weco.pipeline.transformer.mets.generators

import scala.xml.Elem

trait PremisAccessConditionsGenerators {

  def rightsMDWith(
                    copyrightInformation: Option[Elem] = None,
                    licenceInformation: Option[Elem] = None,
                    rightsGranted: Seq[Elem] = Nil
                  ): Elem = <mets:rightsMD ID="rightsMD_1">
    <mets:mdWrap MDTYPE="PREMIS:RIGHTS">
      <mets:xmlData>
        <premis:rightsStatement xmlns:premis="http://www.loc.gov/premis/v3" xsi:schemaLocation="http://www.loc.gov/premis/v3 http://www.loc.gov/standards/premis/v3/premis.xsd">
          <premis:rightsStatementIdentifier>
            <premis:rightsStatementIdentifierType>UUID</premis:rightsStatementIdentifierType>
            <premis:rightsStatementIdentifierValue>3392668a-4503-462c-ba68-1d17c853f17c</premis:rightsStatementIdentifierValue>
          </premis:rightsStatementIdentifier>
          <premis:rightsBasis>???</premis:rightsBasis>{copyrightInformation}{licenceInformation}{rightsGranted}
        </premis:rightsStatement>
      </mets:xmlData>
    </mets:mdWrap>
  </mets:rightsMD>

  lazy val emptyRightsMD = rightsMDWith(None, None, Nil)
  // This is a real, comprehensive example taken from iiif-stage.wellcomecollection.org
  val openInCopyrightRightsMD = <mets:rightsMD ID="rightsMD_1">
    <mets:mdWrap MDTYPE="PREMIS:RIGHTS">
      <mets:xmlData>
        <premis:rightsStatement xmlns:premis="http://www.loc.gov/premis/v3" xsi:schemaLocation="http://www.loc.gov/premis/v3 http://www.loc.gov/standards/premis/v3/premis.xsd">
          <premis:rightsStatementIdentifier>
            <premis:rightsStatementIdentifierType>UUID</premis:rightsStatementIdentifierType>
            <premis:rightsStatementIdentifierValue>3392668a-4503-462c-ba68-1d17c853f17c</premis:rightsStatementIdentifierValue>
          </premis:rightsStatementIdentifier>
          <premis:rightsBasis>Copyright</premis:rightsBasis>
          <premis:copyrightInformation>
            <premis:copyrightStatus>copyrighted</premis:copyrightStatus>
            <premis:copyrightJurisdiction>UK</premis:copyrightJurisdiction>
            <premis:copyrightStatusDeterminationDate/>
            <premis:copyrightNote>In copyright</premis:copyrightNote>
          </premis:copyrightInformation>
          <premis:rightsGranted>
            <premis:act>use</premis:act>
            <premis:rightsGrantedNote>Open</premis:rightsGrantedNote>
          </premis:rightsGranted>
          <premis:linkingObjectIdentifier>
            <premis:linkingObjectIdentifierType>UUID</premis:linkingObjectIdentifierType>
            <premis:linkingObjectIdentifierValue>3eecb018-4569-4fa1-bc3d-eb06c8eaeabd</premis:linkingObjectIdentifierValue>
          </premis:linkingObjectIdentifier>
        </premis:rightsStatement>
      </mets:xmlData>
    </mets:mdWrap>
  </mets:rightsMD>

  val openCCBYNCRightsMD = <mets:rightsMD ID="rightsMD_1">
    <mets:mdWrap MDTYPE="PREMIS:RIGHTS">
      <mets:xmlData>
        <premis:rightsStatement xmlns:premis="http://www.loc.gov/premis/v3" xsi:schemaLocation="http://www.loc.gov/premis/v3 http://www.loc.gov/standards/premis/v3/premis.xsd">
          <premis:rightsStatementIdentifier>
            <premis:rightsStatementIdentifierType>UUID</premis:rightsStatementIdentifierType>
            <premis:rightsStatementIdentifierValue>a93acc1b-7978-4107-80f7-a4bdf5787a85</premis:rightsStatementIdentifierValue>
          </premis:rightsStatementIdentifier>
          <premis:rightsBasis>License</premis:rightsBasis>
          <premis:licenseInformation>
            <premis:licenseTerms/>
            <premis:licenseNote>CC-BY-NC</premis:licenseNote>
          </premis:licenseInformation>
          <premis:rightsGranted>
            <premis:act>use</premis:act>
            <premis:rightsGrantedNote>Open</premis:rightsGrantedNote>
          </premis:rightsGranted>
          <premis:linkingObjectIdentifier>
            <premis:linkingObjectIdentifierType>UUID</premis:linkingObjectIdentifierType>
            <premis:linkingObjectIdentifierValue>433cbf46-4ce1-421d-bf4f-2bc13f9450a7</premis:linkingObjectIdentifierValue>
          </premis:linkingObjectIdentifier>
        </premis:rightsStatement>
      </mets:xmlData>
    </mets:mdWrap>
  </mets:rightsMD>

  // This should never happen in practice, as we should always have a rightsBasis
  // but we fail gracefully if it does, preferring copyrightInformation
  val openMixedCCBYNCWithCopyrightRightsMD = <mets:rightsMD ID="rightsMD_1">
    <mets:mdWrap MDTYPE="PREMIS:RIGHTS">
      <mets:xmlData>
        <premis:rightsStatement xmlns:premis="http://www.loc.gov/premis/v3" xsi:schemaLocation="http://www.loc.gov/premis/v3 http://www.loc.gov/standards/premis/v3/premis.xsd">
          <premis:rightsStatementIdentifier>
            <premis:rightsStatementIdentifierType>UUID</premis:rightsStatementIdentifierType>
            <premis:rightsStatementIdentifierValue>a93acc1b-7978-4107-80f7-a4bdf5787a85</premis:rightsStatementIdentifierValue>
          </premis:rightsStatementIdentifier>
          <premis:licenseInformation>
            <premis:licenseTerms/>
            <premis:licenseNote>CC-BY-NC</premis:licenseNote>
          </premis:licenseInformation>
          <premis:copyrightInformation>
            <premis:copyrightStatus>copyrighted</premis:copyrightStatus>
            <premis:copyrightJurisdiction>UK</premis:copyrightJurisdiction>
            <premis:copyrightStatusDeterminationDate/>
            <premis:copyrightNote>In copyright</premis:copyrightNote>
          </premis:copyrightInformation>
          <premis:rightsGranted>
            <premis:act>use</premis:act>
            <premis:rightsGrantedNote>Open</premis:rightsGrantedNote>
          </premis:rightsGranted>
          <premis:linkingObjectIdentifier>
            <premis:linkingObjectIdentifierType>UUID</premis:linkingObjectIdentifierType>
            <premis:linkingObjectIdentifierValue>433cbf46-4ce1-421d-bf4f-2bc13f9450a7</premis:linkingObjectIdentifierValue>
          </premis:linkingObjectIdentifier>
        </premis:rightsStatement>
      </mets:xmlData>
    </mets:mdWrap>
  </mets:rightsMD>
}

