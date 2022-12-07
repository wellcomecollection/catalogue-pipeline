# Our single model: the Work

All of our source catalogues store data in a different way — for example, our archives catalogue uses [ISAD(G)], whereas our library catalogue uses [MARC].

We transform all of these records into a common model called a **Work**.

This model allows us to:

*   Store all the data we want to present publicly
*   Search all of our records together, rather than searching the source catalogues individually and trying to combine the results.
*   Preserve the relationship and hierarchy between works.
    This is particularly important for archives and series; previous versions of our catalogue search would flatten these hierarchies, which made it harder to find context if you were looking at a single record.

The exact structure of the Work model varies over time; the best reference is the [API documentation for the works API][worksapi].

[ISAD(G)]: https://en.wikipedia.org/wiki/ISAD(G)
[MARC]: https://en.wikipedia.org/wiki/MARC_standards
[worksapi]: https://developers.wellcomecollection.org/api/catalogue#tag/Works/operation/getWork

## Transforming source records into Works

We have a different transformation process for each source catalogue, based on how it structures its data.
For example, the "title" on a Work created from our archive catalogue comes from the "Title" field, whereas a Work from our library catalogue uses MARC field 245.

There is no complete reference or specification for this mapping.
There's some documentation that outlines broad strokes (e.g. use MARC 245 for the title), but there are lots of details which can only be found by reading the transformer code (e.g. which MARC subfields we do/don't present).

We try to avoid "cleverness" in the transformation process.
e.g. where possible, we don't want to "fix" problems with the data in code -- we should work with the Collections team to fix the data in the source catalogue.

This transformation process is continually improved and refined, in collaboration with the Collections team.

## Works and Items

The word "item" is often used informally, but it has a specific meaning in the context of the catalogue pipeline.

*   A **Work** is an intellectual entity, e.g. *Notes on Nursing* by Florence Nightingale.

*   An **Item** is a specific manifestation of a Work, for example a physical copy of the book *Notes on Nursing* by Florence Nightingale.
    A single Work might have multiple Items, for example if the library owns more than one copy of a book.

If you're familiar with library cataloguing: works/items are similar to bibs/items.
