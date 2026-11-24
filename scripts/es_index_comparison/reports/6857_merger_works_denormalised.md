# Scala vs Python merger: causes of the differences

platform#6857. This report compares the works merged by the Scala merger (`works-denormalised-2026-09-30`) with the works merged by the Python merger (`works-denormalised-2026-11-17`). Both indices were dumped at 11:40 UTC on 19 November 2026. The Python merger had last run at 10:35 UTC that day, reading the `works_identified_2026_09_30` Iceberg table as committed at 10:35:55.

The one exception is two works, `g8jqs6n2` and `xe4fbwjx`, whose Python copies were taken again on 24 November. The table was missing a Deleted row for `g8jqs6n2`, left out by the registry repair in platform#6754. The row was added on 23 November and the Python merger run that day fixed both works.

The comparison uses `configs/6857_merger_works_denormalised.yaml`. Of 3,058,030 works in both indices, 3,054,926 are identical and 3,104 differ, with 13,121 differences between them. 11 works are only in the Scala index, and none are only in the Python index.

## Method

The differences are grouped by cluster rather than by work or by field. A cluster is the set of works that either merger grouped together, plus the internal works of any TEI work in it. Scala's groups are read from the redirects in its index, and Python's from its matcher components. When the two mergers group works differently, all the works involved land in one cluster.

Each cluster has exactly one cause. The checks run in the order of the sections below, and the first that matches wins. So a basic cause, such as a different input, accounts for every difference that follows from it. For example, when the mergers choose different targets, the work that is Visible in one index is Redirected in the other. That shows up as differences in `type`, `data`, `redirectTarget`, `redirectSources` and every merged field, all counted under the one cause.

Every difference is accounted for. No cluster was left unclassified.

## Summary

| Cause | Clusters | Works | Differences | Only in Scala | Right |
|---|---|---|---|---|---|
| **Inputs** | | | | | |
| Internal work listed by several TEI works | 13 | 23 | 149 | 2 | Neither |
| Several TEI works linked to one Sierra bib | 8 | 11 | 42 | 9 | Neither |
| Scala missed an update | 3 | 3 | 13 | | Python |
| **Scala matcher faults** | | | | | |
| METS work made invisible | 148 | 308 | 1,594 | | Python |
| Axiell work left out of its cluster | 23 | 54 | 264 | | Python |
| Sierra work left out of its cluster | 16 | 39 | 147 | | Python |
| Target doesn't list all works redirected to it | 63 | 65 | 297 | | Python |
| **Different choice between equal works** | | | | | |
| Two e-bibs, a different one merged | 696 | 2,088 | 8,605 | | Either |
| Two physical Sierra bibs in one component | 2 | 7 | 33 | | Python |
| Miro work linked to several targets | 124 | 410 | 1,774 | | Either |
| METS work linked to two Sierra works | 11 | 33 | 110 | | Either |
| Thumbnail from a different METS work | 63 | 63 | 93 | | Either |
| **Total** | **1,170** | **3,104** | **13,121** | **11** | |

"Works" counts the works with at least one difference. A cluster can also hold works that are identical in both indices.
## Inputs

### Internal work listed by several TEI works

13 clusters, 23 differing works, 2 works only in Scala.

Each merger writes an internal work once for every TEI work listing it, with that TEI work's version and fields. When several TEI works list the same internal work, the last write wins, in both mergers, so the two indices end up with different copies.

For example, `uec5dvyx` (MS_3_1) is listed by three TEI works, `MS_3`, `Tamil_3` and `Tamil_ MS 3`. The Scala index has the `Tamil_3` copy and the Python index has the `Tamil_ MS 3` copy. `hsgjjuyc` has an empty source identifier and is listed by both MS_Tibetan_133 and MS_Tibetan_134.

Across the table, 28 of the 2,399 internal works listed by Visible TEI works are listed by more than one. This is a TEI data problem: several TEI files describe the same manuscript, or their internal identifiers clash. For example, `MS_Amer_16` lists `MS_Amer_6_1` as its internal work. Neither merger can resolve it.

Two of these clusters, MS Amer 4 and 6 and MS Amer 12 and 16, also hold several TEI works linked to one Sierra bib, as in the next section. Their 2 works only in Scala are internal works of the unmerged TEI work.

### Several TEI works linked to one Sierra bib

8 clusters, 11 differing works, 9 works only in Scala.

Both mergers expect at most one TEI work in a component. A TEI work can only be a target, so no rule merges a second TEI work into the first, and it is passed through unmerged. Across the table, 12 components hold more than one Visible TEI work, each listing the same Sierra bib as a merge candidate:

| Kind | TEI works | In this report |
|---|---|---|
| The same manuscript under two file names | MS Amer 20, 28, 33, 37, 40, 46 | This section |
| | Tamil 3 | Internal work listed by several TEI works |
| | MS Amer 2 | No difference |
| Different manuscripts sharing one bib | MS Amer 69, 70 and 71, MS2294 and MS2322 | This section |
| | MS Amer 4 and 6, MS Amer 12 and 16 | Internal work listed by several TEI works |

The duplicates are a data error. A bib describing a group of manuscripts may be a deliberate cataloguing choice, which neither merger handles.

Each merger merges the bib into one TEI work and leaves the others unmerged. For example:

```
Scala:  tdvm9pgf (TEI, MS Amer 40) ← cvh4x4th (Sierra)
        bbgcvjwq (TEI, MS_Amer_40)
Python: bbgcvjwq (TEI, MS_Amer_40) ← cvh4x4th (Sierra)
        tdvm9pgf (TEI, MS Amer 40)
```

Neither merger writes internal works for a TEI work it leaves unmerged. In the Scala index, the unmerged TEI work's internal works keep whatever version Scala last wrote, which is how `bbgzjkbn` and `bbtx8vye` came to be at versions from 1 October. The Python index was built from scratch, so it has no internal works for the unmerged TEI work at all. That explains 9 of the 11 works only in Scala, and the other 2 are in the previous section.

MS Amer 2 shows the same gap without a difference. Both mergers chose `MS_Amer_2`, and the internal work of `MSAmer2` (`h85s3q4j`) is missing from both indices.

The Python merger matches Scala here. Writing the internal works of every Visible TEI work it outputs, not only of the target, would be a deliberate change from Scala that covers both kinds.

### Scala missed an update

3 clusters, 3 differing works.

For `e8jb3dsu` and `gm2ffmjw` (Axiell) and `nz72348k` (Sierra, Deleted), `works-identified-2026-09-30` has held a newer version for between 8 and 31 days. The Scala index still has the copy merged on 7 or 8 October. The Python merger read the same newer version from the table.

## Scala matcher faults

In these clusters, the Scala matcher's graph (`catalogue-2026-09-30_works-graph`) has lost a link between works, so the Scala merger never saw them together. All the works involved were last merged on 1 October, during the reindex. The graph has two kinds of fault:

- **Placeholder nodes.** The matcher keeps a placeholder node, with no work data, for a merge candidate it hasn't seen yet, and never sends placeholders to the merger. In these clusters, the node of a real work has been replaced by a placeholder. Most likely, two linked works were processed at the same moment and the later write didn't see the earlier one's node.
- **Components that disagree.** The matcher finds a work's neighbours through an index that can lag behind recent writes. A work processed just after a linked work was written can miss it and save itself as a component of its own. One node then says the two works share a component, and the other says it's alone.

| Cause | Placeholder | Components disagree |
|---|---|---|
| METS work made invisible | | 148 clusters |
| Axiell work left out of its cluster | 21 clusters | 2 clusters |
| Sierra work left out of its cluster | 15 clusters | 1 cluster |
| Target doesn't list all works redirected to it | 77 works | 1 work |

Which difference a fault produces depends on which works Scala merged again afterwards. The Python matcher computes components from the whole table on every run, so it has neither fault.

### METS work made invisible

148 clusters, 308 differing works.

A METS work that isn't merged into anything is Invisible ("MetsWorksAreNotVisible"). After the graph broke, Scala merged 148 METS works on their own, and Python merged each into its Sierra work:

```
Scala:  a6qr9bf2 (Sierra)
        ff7dgp9h (METS, Invisible)
Python: a6qr9bf2 (Sierra) ← ff7dgp9h (METS)
```

The Sierra work then differs in items, availabilities and thumbnail as well.

### Axiell work left out of its cluster

23 clusters, 54 differing works.

The Python merger merges a Sierra, METS or Miro work into an Axiell target where Scala keeps them apart. For example, Scala merged Axiell work `ayrs4ry7` at 14:37:36 on 1 October and Sierra work `vybyxdr3` 3 seconds later, and the graph now holds only a placeholder for `ayrs4ry7`:

```
Scala:  ayrs4ry7 (Axiell)
        vybyxdr3 (Sierra)
Python: ayrs4ry7 (Axiell) ← vybyxdr3 (Sierra)
```

In 15 of the 23, Scala's own copies also contradict each other: its Axiell work lists a work as merged into it that Scala redirected elsewhere. For example, `h96n8am5` lists METS work `g3mhqtxq`, which redirects to `g7dqvfvw`.

### Sierra work left out of its cluster

16 clusters, 39 differing works.

Scala left a Sierra work unmerged that Python merged into a linked Sierra work, usually an e-bib into its physical bib.

### Target doesn't list all works redirected to it

63 clusters, 65 differing works.

78 works are Redirected to a target in the Scala index, but that target's `redirectSources` doesn't list them: 55 Sierra, 22 Miro and 1 METS work. Scala merged them into the target on 1 October. After the graph broke, Scala merged the target again without them. It writes only the works the matcher sends it, so the old redirects were never updated. Python lists all 78, along with the items, identifiers and availabilities they bring.

## Different choice between equal works

In these clusters, both mergers had the same works, and the Scala graph holds them in one component. Several of the works were equally good candidates. The Python merger sorts a component's works by canonical id before merging, so it takes the candidate with the lowest id, a stable but arbitrary choice. The Scala merger takes the first candidate in the order its matcher returns the works, which isn't stable. Only the clusters where the two happened to differ appear here.

### Two e-bibs, a different one merged

696 clusters, 2,088 differing works.

A physical bib linked to two e-bibs merges one of them and leaves the other alone. In all 696, Python merged the e-bib with the lower id:

```
Scala:  yxhaxrw2 (Sierra) ← xewdkns6 (Sierra)
        a2drwhqt (Sierra)
Python: yxhaxrw2 (Sierra) ← a2drwhqt (Sierra)
        xewdkns6 (Sierra)
```

### Two physical Sierra bibs in one component

2 clusters, 7 differing works.

Both mergers make a physical Sierra bib the target, and a component can hold two. Scala takes the first in matcher order, and Python the lower id. For example, `grfprecn` lists `hpp7wkz8` as its "Physical/digitised Sierra work", but both are physical bibs in closed stores. Scala made `hpp7wkz8` the target, which has no merge candidates, so no rule claims `grfprecn`. Python made `grfprecn` the target, and its link claims `hpp7wkz8`:

```
Scala:  grfprecn (Sierra)
        hpp7wkz8 (Sierra)
Python: grfprecn (Sierra) ← hpp7wkz8 (Sierra)
```

In the other cluster, `qgw8dapu` and `dezz68z5` each have their own e-bib and METS work, and share a Miro work. Scala merged everything it could into `qgw8dapu`, including the METS work of `dezz68z5`'s e-bib, and left `dezz68z5` and its e-bib unmerged. Python merged each bib with its own e-bib and METS work. Python's result is better in both.

### Miro work linked to several targets

124 clusters, 410 differing works.

A Miro work listed by several Sierra or Axiell works is merged into one of them. For 140 of the 149 Miro works involved, both mergers pick the work that wins target precedence, but they break ties differently. Python takes the lowest id, and Scala takes the first in the matcher's order:

```
Scala:  h2bqrjya (Sierra) ← kuw9t86g (Miro)
        a5qg94r7 (Sierra)
Python: a5qg94r7 (Sierra) ← kuw9t86g (Miro)
        h2bqrjya (Sierra)
```

The other 9 Miro works are each listed by only one bib, and Python merges them into that bib. Scala merged them into a different bib on 1–3 Oct, before its fix for platform#6750 was deployed, and hasn't merged them again since.

The Miro work brings its image, so these clusters also differ in `imageData`, `thumbnail` and items.

### METS work linked to two Sierra works

11 clusters, 33 differing works.

A METS work's identifier is the b-number of the Sierra bib it digitises. When it's linked to two Sierra works, Python always merged it into the one with the lower id. Scala chose the METS work's own bib in 8 of the 11, Python in 3:

```
Scala:  the5j2hb (Sierra) ← b9ae464p (METS)
        maw7cjxh (Sierra)
Python: maw7cjxh (Sierra) ← b9ae464p (METS)
        the5j2hb (Sierra)
```

### Thumbnail from a different METS work

63 clusters, 63 differing works.

The same works are merged, but the target has two or three METS works and the mergers take the thumbnail from different ones.

## Suggested actions

1. **Python merger:** write the internal works of every Visible TEI work in the output, not only of the target. This restores the 11 internal works only in Scala and the one missing from both indices.
2. **TEI data:** raise the duplicate TEI records (8 manuscripts), the 4 Sierra bibs linked to several manuscripts, and the internal works listed by several TEI works (28 in the table).
