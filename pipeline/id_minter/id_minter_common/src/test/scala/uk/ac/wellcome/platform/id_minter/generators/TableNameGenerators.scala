package uk.ac.wellcome.platform.id_minter.generators

import scala.util.Random

trait TableNameGenerators {
  // Something in our MySQL Docker image gets upset by some database names,
  // and throws an error of the form:
  //
  //    You have an error in your SQL syntax; check the manual that
  //    corresponds to your MySQL server version for the right syntax to use
  //
  // This error can be reproduced by running "CREATE DATABASE <name>" inside
  // the Docker image.  It's not clear what features of the name cause this
  // error to occur, but so far we've only seen it in database names that
  // include numbers.  We're guessing some arrangement of numbers causes the
  // issue; for now we just use letters to try to work around this issue.
  //
  // The Oracle docs are not enlightening in this regard:
  // https://docs.oracle.com/database/121/SQLRF/sql_elements008.htm#SQLRF00223

  // This is based on the implementation of alphanumeric in Scala.util.Random.
  private def alphabetic: Stream[Char] = {
    def nextAlpha: Char = {
      val chars = "abcdefghijklmnopqrstuvwxyz"
      chars charAt Random.nextInt(chars.length)
    }

    Stream continually nextAlpha
  }

  def createDatabaseName: String =
    s"db_${alphabetic take 10 mkString}"

  def createTableName: String =
    s"tbl_${alphabetic take 10 mkString}"
}
