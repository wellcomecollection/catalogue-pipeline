package uk.ac.wellcome.platform.api.models

import java.time.{LocalDate, Period}

Object {  type R = List[Int] }

sealed trait WorkAggregation



// TODO: have a more abstract class that can take 1x00y as a argument
//final case class CenturyAggregation(from: LocalDate, to: LocalDate)
//  extends WorkAggregation {
//
//
//  def map[T](f: (Range) => T, list: List[Range]): List[T]
////  def map(acc: List[(LocalDate, LocalDate)]): List[(LocalDate, LocalDate)] = {
////    val next = from.plusYears(100)
////    if (next.isBefore(to)) {
////      map
////    } else {
////      acc
////    }
////  }
//}
