package weco.pipeline.transformer.marc_common.transformers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.marc_common.models.{MarcFieldOps, MarcRecord}
import weco.pipeline.transformer.marc_common.transformers.subjects.{
  MarcConceptSubject,
  MarcMeetingSubject,
  MarcOrganisationSubject,
  MarcPersonSubject
}

object MarcSubjects extends MarcDataTransformer with MarcFieldOps {

  override type Output = Seq[Subject[IdState.Unminted]]

  /** Our cataloguing practise encodes the following rules with respect to which
    * headings we choose from the MARC record in 6xx fields, which we ignore and
    * what concept type they map to.
    *
    * See: https://www.loc.gov/marc/bibliographic/bd6xx.html - Subject Access
    * Fields https://www.loc.gov/marc/bibliographic/bd650.html650 - Subject
    * Added Entry - Topical Term (MarcConceptSubject)
    * https://www.loc.gov/marc/bibliographic/bd651.html651 - Subject Added Entry
    * \- Geographic Name (MarcConceptSubject)
    * https://www.loc.gov/marc/bibliographic/bd648.html648 - Subject Added Entry
    * \- Chronological Term (MarcConceptSubject)
    * https://www.loc.gov/marc/bibliographic/bd600.html600 - Subject Added Entry
    * \- Personal Name (MarcPersonSubject)
    * https://www.loc.gov/marc/bibliographic/bd610.html610 - Subject Added Entry
    * \- Corporate Name (MarcOrganisationSubject)
    * https://www.loc.gov/marc/bibliographic/bd611.html611 - Subject Added Entry
    * \- Meeting Name (MarcMeetingSubject)
    *
    * We catalogue using LoC (2nd indicator 0) and MeSH (2nd indicator 2) and
    * other (2nd indicator 7), so for the above Subject Added Entry's we do not
    * take any headings with 2nd indicators 1, 3-6, there are particular rules
    * on 2nd indicator 7 headings:
    *
    * We currently keep/use the following 650_7 ǂ2: local, homoit, indig, enslv
    *
    * See https://www.loc.gov/standards/sourcelist/subject.html for a list of
    * subject sources.
    *
    * Consult the Collections Information Team for further information or when
    * making changes.
    */

  val transformMap = Map(
    "600" -> MarcPersonSubject,
    "610" -> MarcOrganisationSubject,
    "611" -> MarcMeetingSubject,
    "650" -> MarcConceptSubject,
    "648" -> MarcConceptSubject,
    "651" -> MarcConceptSubject
  )

  private lazy val marcTags = transformMap.keys.toSeq
  override def apply(record: MarcRecord): Output = {
    record
      .fieldsWithTags(marcTags: _*)
      .filter {
        field =>
          {
            field.indicator2 match {
              case "0" | "2" => true
              case "7" =>
                field
                  .subfieldsWithTag("2")
                  /** The below strings are "subject sources". See:
                    * https://www.loc.gov/standards/sourcelist/subject.html
                    *
                    * "Subject Sources identifies subject heading lists,
                    * thesauri, and databases that are the sources of topical,
                    * geographic, chronological, and other headings or terms
                    * used to describe the subject content of a resource"
                    *
                    * For example "homoit" is "an international linked data
                    * vocabulary of LGBTQ+ terms.", see: https://homosaurus.org/
                    */
                  .exists(
                    subjectSource =>
                      Seq("local", "homoit", "indig", "enslv")
                        .contains(subjectSource.content)
                  )

              case _ => false
            }
          }
      }
      .flatMap {
        field => transformMap(field.marcTag)(field)
      }
  }
}
