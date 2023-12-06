package weco.pipeline.transformer.mets.generators

trait PremisAccessConditionsGenerators {

  val inCopyrightRightsMD = <mets:rightsMD ID="rightsMD_1">
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
}
