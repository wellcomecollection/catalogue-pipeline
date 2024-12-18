package weco.pipeline.ingestor.common.models

import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.languages.MarcLanguageCodeList
import weco.catalogue.internal_model.work.{Availability, WorkData}

import java.time.{LocalDate, ZoneOffset}

trait AggregatableValues {
  implicit class WorkDataOps(workData: WorkData[DataState.Identified]) {
    def genreAggregatableValues: List[AggregatableField] =
      workData.genres.map(
        genre =>
          AggregatableField.fromIdState(
            genre.concepts.headOption.map(_.id),
            genre.label
          )
      )

    def subjectAggregatableValues: List[AggregatableField] =
      workData.subjects.map(
        subject =>
          AggregatableField.fromIdState(Some(subject.id), subject.label)
      )

    def contributorAggregatableValues: List[AggregatableField] =
      workData.contributors
        .map(_.agent)
        .map(
          agent => AggregatableField.fromIdState(Some(agent.id), agent.label)
        )

    def licenseAggregatableValues: List[AggregatableField] =
      workData.items
        .flatMap(_.locations)
        .flatMap(_.license)
        .map(license => AggregatableField(license.id, license.label))

    def languageAggregatableValues: List[AggregatableField] =
      workData.languages
        .map(
          lang =>
            // There are cases where two languages may have the same ID but different
            // labels, e.g. Chinese and Mandarin are two names for the same language
            // which has MARC language code "chi".  The distinct names may be important
            // for display on individual works pages, but for filtering/aggregating
            // we want to use the canonical labels.
            MarcLanguageCodeList.fromCode(lang.id) match {
              case Some(canonicalLang) => canonicalLang
              case None                => lang
            }
        )
        .distinct
        .map(language => AggregatableField(language.id, language.label))

    def workTypeAggregatableValues: List[AggregatableField] =
      workData.format.toList
        .map(format => AggregatableField(format.id, format.label))

    // Note: this is based on the previous Elasticsearch behaviour, which aggregated over
    // the start date of the periods.
    //
    // It's not obvious to me if aggregating by start date is the "best" behaviour,
    // or if it's the best we could squeeze into Elasticsearch terms aggregations.
    // I'm going to leave it as-is for now, but this is a note that we can revisit this
    // (and other aggregations) at some point, because this approach gives us more flexibility.
    def productionDateAggregatableValues: List[AggregatableField] =
      workData.production
        .flatMap(_.dates)
        .flatMap(_.range)
        .map(
          // Extract the year part from the start of the range.
          // range.from is an Instant with an underlying representation in Epoch Time.
          // Extracting the year using LocalDateTime requires it to be first localised
          // then the year in Local Time is extracted.
          // If the epoch time is close to either end of a year, then a non-zero timezone
          // offset could cause the "wrong" year to be returned.
          // This will be the case when range represents a whole year or range of years
          // where _.from is the very beginning of the year.
          range => LocalDate.ofInstant(range.from, ZoneOffset.UTC).getYear
        )
        .map(
          startYear => AggregatableField.fromLabel(label = startYear.toString)
        )
  }

  implicit class AvailabilityOps(availabilities: Set[Availability]) {
    def aggregatableValues: List[AggregatableField] =
      availabilities
        .map(
          availability => AggregatableField(availability.id, availability.label)
        )
        .toList
  }
}
