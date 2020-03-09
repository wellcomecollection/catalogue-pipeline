# Test 6 - `ConstScore` vs `BoolBoosted`

## Overview

Currently we are matching [_named features_](#glossary-named-feature) with a simple **AND** query.

This means that if something has the `title` of `Treatise on Radioactivity`, all of
the below will match with equal scoring:
- `Treatise on Radioactivity`
- `Radioactivity on Treatise`
- `on Radioactivity Treatise`
- etc

This tests whether [`phrase matching`](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query-phrase.html)
is a better fit for this. You would think so given that is what it is for.

We wrap the query in a [`multi_match query`](https://www.elastic.co/guide/en/elasticsearch/reference/7.6/query-dsl-multi-match-query.html),
matching on the `text` type and `keyword` type fields, boosting the
`keyword` field higher as that would infer an exact match.

Using the `phrase` type of `multi_match` then chooses the highest score of the two, and surfaces that.

Because we the lose the niceness of the `AND` search, we've added that as a tier,
applied it similarly to the `BaseQuery` but boosted it by 2.

We think this should give us much better matching of items people know the name of,
but retain the fetching of things that are loosely relevant.

### Known unknowns
After running through the explain API with this, boosting seems to be similar,
but not exactly the same as what you put in the query, we'll be exploring further
as to why that is the case.

### Glossary

<h4 id="glossary-named-feature">Named feature</h4>
A named feature is a piece of data in which the whole phrase contains
semantic meaning. e.g. subjects, genres, titles, people and organisation names etc.

## Results

TBD

### Click through rate

|                   | ConstScore | BoolBoosted |
| :---------------- | :--------- | :---------- |
| first page only   | TBD        | TBD         |
| beyond first page | TBD        | TBD         |

### Click distribution

TBD

## Conclusions

TBD
