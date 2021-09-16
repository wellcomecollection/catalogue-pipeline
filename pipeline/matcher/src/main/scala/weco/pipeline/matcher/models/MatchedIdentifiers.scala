package weco.pipeline.matcher.models

// Represents a collection of identifiers that represent the same Work.
//
// The identifiers in this set should be merged together.
//
// For example, if the matcher sends MatchedIdentifiers({A, B, C}), it
// means the merger should combine these into a single work.
//
case class MatchedIdentifiers(workCollections: Set[WorkStub])
