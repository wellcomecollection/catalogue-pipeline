package weco.pipeline.transformer.mets.generators

import weco.catalogue.internal_model.locations.License
import weco.fixtures.RandomGenerators

import scala.xml.{Elem, NodeSeq}

trait MetsGenerators extends RandomGenerators {
  def metsXmlWith(
    recordIdentifier: String,
    title: String = randomAlphanumeric(),
    license: Option[License] = None,
    accessConditionStatus: Option[String] = None,
    accessConditionUsage: Option[String] = None,
    secondarySections: NodeSeq = NodeSeq.Empty,
    fileSec: NodeSeq = NodeSeq.Empty,
    structMap: NodeSeq = NodeSeq.Empty
  ) =
    <mets:mets xmlns:dv="http://dfg-viewer.de/" xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:premis="http://www.loc.gov/premis/v3" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" OBJID="276914" xsi:schemaLocation="http://www.loc.gov/standards/premis/ http://www.loc.gov/standards/premis/v2/premis-v2-0.xsd http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd http://www.loc.gov/METS/ http://www.loc.gov/standards/mets/mets.xsd http://www.loc.gov/standards/mix/ http://www.loc.gov/standards/mix/mix.xsd">

      {goobiHeader}
      {
      rootSection(
        recordIdentifier = recordIdentifier,
        title = title,
        license = license,
        accessConditionStatus = accessConditionStatus,
        accessConditionUsage = accessConditionUsage
      )
    }
      {secondarySections}
      {fileSec}
      {structMap}
     </mets:mets>.toString()

  def xmlWithManifestations(
    manifestations: List[(String, String, String)],
    title: String = randomAlphanumeric()
  ) =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xlink="http://www.w3.org/1999/xlink">
      {goobiHeader}
      {
      rootSection(
        recordIdentifier = "b30246039",
        title = title,
        license = None,
        accessConditionStatus = None,
        accessConditionUsage = None
      )
    }
      <mets:structMap TYPE="LOGICAL">
        <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" TYPE="MultipleManifestation">
          {
      manifestations.map {
        case (id, order, filename) =>
          <mets:div ID={id} ORDER={order} TYPE="Monograph">
                <mets:mptr LOCTYPE="URL" xlink:href={filename} />
              </mets:div>
      }
    }
        </mets:div>
      </mets:structMap>
    </mets:mets>

  protected val goobiHeader: Elem =
    <mets:metsHdr CREATEDATE="2016-09-07T09:38:57">
      <mets:agent OTHERTYPE="SOFTWARE" ROLE="CREATOR" TYPE="OTHER">
        <mets:name>Goobi - ugh-3.0-ugh-2.0.0-24-gb21188e - 15−January−2016</mets:name>
        <mets:note>Goobi</mets:note>
      </mets:agent>
    </mets:metsHdr>

  def rootSection(
    recordIdentifier: String,
    title: String,
    license: Option[License],
    accessConditionStatus: Option[String],
    accessConditionUsage: Option[String]
  ) =
    <mets:dmdSec ID="DMDLOG_0000">
      <mets:mdWrap MDTYPE="MODS">
        <mets:xmlData>
          <mods:mods>
            <mods:recordInfo>
              <mods:recordIdentifier source="gbv-ppn">{
      recordIdentifier
    }</mods:recordIdentifier>
            </mods:recordInfo>
            <mods:titleInfo>
              <mods:title>{title}</mods:title>
            </mods:titleInfo>
            {
      license.fold(ifEmpty = NodeSeq.Empty) {
        l =>
          <mods:accessCondition type="dz">{
            l.id.toUpperCase
          }</mods:accessCondition>
      }
    }
            {
      accessConditionStatus.fold(ifEmpty = NodeSeq.Empty) {
        a =>
          <mods:accessCondition type="status">{a}</mods:accessCondition>
      }
    }
            {
      accessConditionUsage.fold(ifEmpty = NodeSeq.Empty) {
        a =>
          <mods:accessCondition type="usage">{a}</mods:accessCondition>
      }
    }
          </mods:mods>
        </mets:xmlData>
      </mets:mdWrap>
    </mets:dmdSec>

  def metsSecondarySection(accessConditionStatus: String) =
    <mets:dmdSec ID="DMDLOG_0001">
      <mets:mdWrap MDTYPE="MODS">
        <mets:xmlData>
          <mods:mods>
            <mods:accessCondition type="status">{
      accessConditionStatus
    }</mods:accessCondition>
          </mods:mods>
        </mets:xmlData>
      </mets:mdWrap>
    </mets:dmdSec>

  def fileSec(filePrefix: String) =
    <mets:fileSec>
      <mets:fileGrp USE="OBJECTS">
        <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
          <mets:FLocat LOCTYPE="URL" xlink:href={
      s"objects/${filePrefix}_0001.jp2"
    } />
        </mets:file>
        <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
          <mets:FLocat LOCTYPE="URL" xlink:href={
      s"objects/${filePrefix}_0002.jp2"
    } />
        </mets:file>
      </mets:fileGrp>
      <mets:fileGrp USE="ALTO">
        <mets:file ID="FILE_0001_ALTO" MIMETYPE="application/xml">
          <mets:FLocat LOCTYPE="URL" xlink:href={
      s"alto/${filePrefix}_0001.xml"
    } />
        </mets:file>
      </mets:fileGrp>
    </mets:fileSec>

  def structMap =
    <mets:structMap TYPE="PHYSICAL">
    <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
      <mets:div ADMID="AMD_0001" ID="PHYS_0001" TYPE="page">
        <mets:fptr FILEID="FILE_0001_OBJECTS" />
        <mets:fptr FILEID="FILE_0001_ALTO" />
      </mets:div>
      <mets:div ADMID="AMD_0002" ID="PHYS_0002" TYPE="page">
        <mets:fptr FILEID="FILE_0002_OBJECTS" />
      </mets:div>
    </mets:div>
  </mets:structMap>
}
