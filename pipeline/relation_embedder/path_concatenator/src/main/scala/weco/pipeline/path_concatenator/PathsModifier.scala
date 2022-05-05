package weco.pipeline.path_concatenator

//import weco.catalogue.internal_model.work.Work
//import weco.catalogue.internal_model.work.WorkState.Merged

/**
 *  Given a path, Fetch and modify the relevant Works (if necessary)
 *  - Modify the work with that exact path.
 *  - Modify all works under that exact path.
 *
 *  To do this, it needs to also find the path of the record representing
 *  the first node in this path.
 *
 *  So, given records with paths:
 *  - a
 *  - a/b
 *  - c/d
 *  - c/e
 *  - b/c
 *
 *  When RecordModifier encounters b/c, it will
 *  - fetch the path a/b
 *  - change b/c to a/b/c
 *  - change c/d and c/e to a/b/c/d and a/b/c/e, respectively,
 */
object PathsModifier {
//
//  def apply(path: String): Work.Visible[Merged] = {
//
//  }
}
