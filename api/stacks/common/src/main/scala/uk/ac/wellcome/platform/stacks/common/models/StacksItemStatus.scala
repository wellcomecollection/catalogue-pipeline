package uk.ac.wellcome.platform.stacks.common.models

case class StacksItemStatus(id: String, label: String)

object StacksItemStatus {
  def apply(rawCode: String): StacksItemStatus =
    rawCode.trim match {
      case "-"     => StacksItemStatus("available", "Available")
      case "z"     => StacksItemStatus("cl-returned", "CL Returned")
      case "o"     => StacksItemStatus("library-use-only", "Library use only")
      case "n"     => StacksItemStatus("billed-not-paid", "Billed not paid")
      case "$"     => StacksItemStatus("billed-paid", "Billed paid")
      case "t"     => StacksItemStatus("in-transit", "In transit")
      case "!"     => StacksItemStatus("on-holdshelf", "On holdshelf")
      case "l"     => StacksItemStatus("lost", "Lost")
      case "m"     => StacksItemStatus("missing", "Missing")
      case default => StacksItemStatus(default, "Unknown")
    }
}
