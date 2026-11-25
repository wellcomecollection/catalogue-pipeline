# Why the Scala and Python mergers differ

This report compares the works merged by the Scala merger (`works-denormalised-2026-09-30`) with the works merged by the Python merger (`works-denormalised-2026-11-17`). Both indices were dumped at 11:40 UTC on 19 November 2026. The Python merger had last run at 10:35 UTC that day, reading the `works_identified_2026_09_30` Iceberg table as committed at 10:35:55.

The one exception is two works, `g8jqs6n2` and `xe4fbwjx`, whose Python copies were taken again on 24 November. The table was missing a Deleted row for `g8jqs6n2`, left out by the registry repair in platform#6754. The row was added on 23 November and the Python merger run that day fixed both works.

The comparison uses `configs/6857_merger_works_denormalised.yaml`. Of 3,058,030 works in both indices, 3,054,926 are identical and 3,104 differ, with 13,121 differences between them. 11 works are only in the Scala index, and none are only in the Python index.

## Method

The differences are grouped by cluster rather than by work or by field. A cluster is the set of works that either merger grouped together, plus the internal works of any TEI work in it. Scala's groups are read from the redirects in its index, and Python's from its matcher components. When the two mergers group works differently, all the works involved land in one cluster.

Each cluster has exactly one cause. The checks run in the order of the sections below, and the first that matches wins. So a basic cause, such as a different input, accounts for every difference that follows from it. For example, when the mergers choose different targets, the work that is Visible in one index is Redirected in the other. That shows up as differences in `type`, `data`, `redirectTarget`, `redirectSources` and every merged field, all counted under the one cause.

## Summary

| Cause | Clusters | Works | Differences | Only in Scala | Right |
|---|---|---|---|---|---|
| **Inputs** | | | | | |
| Internal work listed by several TEI works | 13 | 23 | 149 | 2 | Neither |
| Several TEI works linked to one Sierra bib | 8 | 11 | 42 | 9 | Neither |
| **Scala pipeline faults** | | | | | |
| Scala missed an update | 3 | 3 | 13 | | Python |
| METS work made invisible | 148 | 308 | 1,594 | | Python |
| Axiell work left out of its cluster | 23 | 54 | 264 | | Python |
| Sierra work left out of its cluster | 16 | 39 | 147 | | Python |
| Target doesn't list all works redirected to it | 63 | 65 | 297 | | Python |
| **Different choice between equal works** | | | | | |
| Two e-bibs, a different one merged | 696 | 2,088 | 8,605 | | Either |
| Two physical Sierra bibs linked as a digitised pair | 1 | 2 | 9 | | Either |
| Miro work linked to several targets | 125 | 415 | 1,798 | | Either |
| METS work linked to two Sierra works | 11 | 33 | 110 | | Either |
| Thumbnail from a different METS work | 63 | 63 | 93 | | Either |
| **Total** | **1,170** | **3,104** | **13,121** | **11** | |

"Works" counts the works with at least one difference. A cluster can also hold works that are identical in both indices.

## Inputs

### Internal work listed by several TEI works

Each merger writes an internal work once for every TEI work listing it, with that TEI work's version and fields. When several TEI works list the same internal work, the last write wins, in both mergers, so the two indices end up with different copies.


For example, `uec5dvyx` (MS_3_1) is listed by three TEI works, `MS_3`, `Tamil_3` and `Tamil_ MS 3`. The Scala index has the `Tamil_3` copy and the Python index has the `Tamil_ MS 3` copy.

Across the table, 28 of the 2,399 internal works listed by Visible TEI works are listed by more than one. This is a TEI data problem. Either several TEI files describe the same manuscript, or two files give an internal work the same identifier. For example, the internal work of `MS_Amer_16` is identified as `MS_Amer_6_1`, which is also the internal work of `MS_Amer_6`. Neither merger can resolve it. Tracked in [platform#6930](https://github.com/wellcomecollection/platform/issues/6930).

### Several TEI works linked to one Sierra bib

Both mergers expect at most one TEI work in a component. When several TEI works list the same Sierra bib, each merger merges the bib into one of them and leaves the others unmerged:

```
Scala:  tdvm9pgf (TEI, MS Amer 40) ← cvh4x4th (Sierra)
        bbgcvjwq (TEI, MS_Amer_40)
Python: bbgcvjwq (TEI, MS_Amer_40) ← cvh4x4th (Sierra)
        tdvm9pgf (TEI, MS Amer 40)
```

Some of these are the same manuscript under two file names, which is a data error. Others are different manuscripts sharing one bib, which may be a deliberate cataloguing choice. Both are tracked in [platform#6930](https://github.com/wellcomecollection/platform/issues/6930).

Neither merger writes internal works for a TEI work it leaves unmerged. The Scala index keeps the copies it wrote earlier, and the Python index, built from scratch, has none. That explains all 11 works only in Scala, 2 of them counted in the previous section.

The Python merger matches Scala here. Writing the internal works of every Visible TEI work, not only of the target, would be a deliberate change from Scala.

## Scala pipeline faults

In the first cause below, the Scala index holds an older version of a work than the index it is built from. The others come from a race condition in the Scala matcher.

The matcher's reads of its graph can lag behind recent writes. When two linked works are processed moments apart, as they were during the reindex on 1 October, the second can read the graph before the first work's node is visible. It then saves a component without the first work. What that breaks depends on which work lists the other as a merge candidate:

- **The second work lists the first.** The matcher finds no node for the first work, so it writes a placeholder, which overwrites the real node. Placeholders aren't sent to the merger, so the first work is left out of its component.
- **The first work lists the second.** The second work saves itself as a component of its own, while the first work's node still says they share one. Later updates to the second work are merged without the first.

The Python matcher computes components from the whole table on every run, so it has neither fault.

### Scala missed an update

Three works (`e8jb3dsu`, `gm2ffmjw` and `nz72348k`) have a newer version in `works-identified-2026-09-30` than in the Scala index, which still has the copies merged on 7 or 8 October. Python merged the newer versions.

For the first two, the matcher's graph also holds the older version, so the update was lost before or in the matcher. For `nz72348k`, the graph holds the newer version, so the merger never wrote it.

### METS work made invisible

A METS work that isn't merged into anything is Invisible. Scala merged 148 METS works on their own, and Python merged each into its Sierra work:

```
Scala:  a6qr9bf2 (Sierra)
        ff7dgp9h (METS, Invisible)
Python: a6qr9bf2 (Sierra) ← ff7dgp9h (METS)
```

The Sierra work then differs in items, availabilities and thumbnail as well.

### Axiell work left out of its cluster

Scala kept an Axiell work apart from a Sierra, METS or Miro work that Python merged into it:

```
Scala:  ayrs4ry7 (Axiell)
        vybyxdr3 (Sierra)
Python: ayrs4ry7 (Axiell) ← vybyxdr3 (Sierra)
```

### Sierra work left out of its cluster

Scala left a Sierra work unmerged that Python merged into a linked Sierra work, usually an e-bib into its physical bib.

### Target doesn't list all works redirected to it

78 works are Redirected to a target in the Scala index whose `redirectSources` doesn't list them. Scala merged them into the target on 1 October, and later merged the target again without them. It writes only the works the matcher sends it, so the old redirects were never updated. Python lists all 78, along with their items and identifiers.

## Different choice between equal works

In these clusters, both mergers had the same works, and the Scala graph holds them in one component. Several of the works were equally good candidates. The Python merger sorts a component's works by canonical id before merging, so it takes the candidate with the lowest id, a stable but arbitrary choice. The Scala merger takes the first candidate in the order its matcher returns the works, which isn't stable. Only the clusters where the two happened to differ appear here.

### Two e-bibs, a different one merged

A physical bib linked to two e-bibs merges one of them and leaves the other alone. In all 696, Python merged the e-bib with the lower id:

```
Scala:  yxhaxrw2 (Sierra) ← xewdkns6 (Sierra)
        a2drwhqt (Sierra)
Python: yxhaxrw2 (Sierra) ← a2drwhqt (Sierra)
        xewdkns6 (Sierra)
```

### Two physical Sierra bibs linked as a digitised pair

`grfprecn` lists `hpp7wkz8` as its digitised copy, but both are physical bibs. Both mergers make the first physical bib the target, and follow this kind of link only when the target is the one holding it:

- With `grfprecn` as the target, its link merges `hpp7wkz8` into it. This is Python's result, because `grfprecn` has the lower id.
- With `hpp7wkz8` as the target, there is no link to follow and both stay separate. This is Scala's result.

```
Scala:  grfprecn (Sierra)
        hpp7wkz8 (Sierra)
Python: grfprecn (Sierra) ← hpp7wkz8 (Sierra)
```

The rules are the same in both mergers, and only the order differs.

### Miro work linked to several targets

A Miro work listed by several Sierra or Axiell works is merged into one of them. For 141 of the 150 Miro works involved, both mergers pick the work that wins target precedence, but they break ties differently. Python takes the lowest id, and Scala takes the first in the matcher's order:

```
Scala:  h2bqrjya (Sierra) ← kuw9t86g (Miro)
        a5qg94r7 (Sierra)
Python: a5qg94r7 (Sierra) ← kuw9t86g (Miro)
        h2bqrjya (Sierra)
```

The other 9 Miro works are each listed by only one bib, and Python merges them into that bib. Scala merged them into a different bib on 1–3 Oct, before its fix for platform#6750 was deployed, and hasn't merged them again since. In one more cluster from before the fix, Scala put the METS works of two bibs on one of them and left the other bib unmerged, where Python merges each bib with its own METS work.

The Miro work brings its image, so these clusters also differ in `imageData`, `thumbnail` and items.

### METS work linked to two Sierra works

A METS work's identifier is the b-number of the Sierra bib it digitises. When it's linked to two Sierra works, Python always merged it into the one with the lower id. Scala chose the METS work's own bib in 8 of the 11, Python in 3:

```
Scala:  the5j2hb (Sierra) ← b9ae464p (METS)
        maw7cjxh (Sierra)
Python: maw7cjxh (Sierra) ← b9ae464p (METS)
        the5j2hb (Sierra)
```

### Thumbnail from a different METS work

The same works are merged, but the target has two or three METS works and the mergers take the thumbnail from different ones.
