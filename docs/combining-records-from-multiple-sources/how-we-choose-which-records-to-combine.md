# How we choose which records to combine

We rely on explicit links in the original catalogue records, and use these for combining works.
For example, when we're looking at the library record for a digitised item, we look in [MARC field 776 $w][marc776] for the ID of the physical item.

We do **not** do any sort of inference or "guessing" about how to combine records.

We use the links in the records to build a directed graph of connections between works:

<img src="./Screenshot 2023-01-19 at 09.20.45.png">

We combine each connected component into a single "merged" work, and we redirect the individual works to the merged work.

[marc776]: https://www.loc.gov/marc/bibliographic/bd776.html
