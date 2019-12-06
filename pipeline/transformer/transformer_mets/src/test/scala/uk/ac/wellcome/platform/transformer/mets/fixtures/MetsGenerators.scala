package uk.ac.wellcome.platform.transformer.mets.fixtures

import uk.ac.wellcome.models.work.internal.License

import scala.xml.Elem

trait MetsGenerators {
  def metsXmlWith(recordIdentifier: String, license: License) =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       <mets:dmdSec ID="DMDLOG_0000">
         <mets:mdWrap MDTYPE="MODS">
           <mets:xmlData>
             <mods:mods>
               <mods:recordInfo>
                 <mods:recordIdentifier source="gbv-ppn">{recordIdentifier}</mods:recordIdentifier>
               </mods:recordInfo>
               <mods:accessCondition type="dz">{license.id.toUpperCase}</mods:accessCondition>
             </mods:mods>
           </mets:xmlData>
         </mets:mdWrap>
       </mets:dmdSec>
     </mets:mets>.toString()

  def xmlWithThumbnailImages(recordIdentifier: String, structMap: Elem = structMap, filePrefix: String => String = identity) =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xlink="http://www.w3.org/1999/xlink">
      <mets:dmdSec ID="DMDLOG_0000">
        <mets:mdWrap MDTYPE="MODS">
          <mets:xmlData>
            <mods:mods>
              <mods:recordInfo>
                <mods:recordIdentifier source="gbv-ppn">{recordIdentifier}</mods:recordIdentifier>
              </mods:recordInfo>
            </mods:mods>
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>
      <mets:fileSec>
        <mets:fileGrp USE="OBJECTS">
          <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
            <mets:FLocat LOCTYPE="URL" xlink:href={s"objects/${filePrefix(recordIdentifier)}_0001.jp2"} />
          </mets:file>
          <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
            <mets:FLocat LOCTYPE="URL" xlink:href={s"objects/${filePrefix(recordIdentifier)}_0002.jp2"} />
          </mets:file>
        </mets:fileGrp>
        <mets:fileGrp USE="ALTO">
          <mets:file ID="FILE_0001_ALTO" MIMETYPE="application/xml">
            <mets:FLocat LOCTYPE="URL" xlink:href={s"alto/${filePrefix(recordIdentifier)}_0001.xml"} />
          </mets:file>
        </mets:fileGrp>
      </mets:fileSec>
      {structMap}
    </mets:mets>

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
