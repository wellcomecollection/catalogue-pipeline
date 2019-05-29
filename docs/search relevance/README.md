# Search relevance
Our current setup for enabling discovery of material on wellcomecollection.org/works is pretty basic. Since going live, api.wellcomecollection.org has used the default elasticsearch `simple_query_string` query type, and we haven't made any significant index-time enhancements (like indexing n-gram tokens) to the underlying data. 

The single point of interaction (the search box) and often hundreds of pages of results to sift through leaves users frustrated, feeling a lack of control over the interface and a weakening trust in the system as a whole.

## Baby steps
Instead of working for months on a complete overhaul of the search functionality and puting it in front of users it in one enormous, jarring release, we're making changes incrementally and measuring the effect on performance as we go. 

Query-side changes only affect the requests that are sent to our elasticsearch index, rather than the data stored in the index itself. We can make lots of query-side changes without much disruption to the rest of the platform, so we've started there. That said, we do intend to come back to test some index-side changes soon.

## Measurement
We're taking a [data informed](https://stacks.wellcomecollection.org/data-informed-not-data-driven-13377c77d198) approach to change at Wellcome Collection; if we're going to make changes to the the product they should be well motivated and well measured throughout their implementation.

### What are we measuring here?
In broad terms, we want to produce a number which tells us whether a candidate change to the search system makes the results more or less relevant to the person searching.

There's a lot of interesting discussion to be had about different search modalities, precision vs serendipity, linked open data & knowledge graphs, and existential questions about what relevance even _is_, dude, but the broad definition above is a useful, concise statement to revert back to.

### Explicit vs implicit measurement
We're collecting data in two distinct ways:
- **Explicit data collection:** users are told that they are part of an experiment, and providing us with information is their primary motive.
- **Implicit data collection:** users know that their data might be used to understand aggregate user behaviour, but their behaviour on the site is driven by another motive.

Some of the data we use to measure search relevance is collected in sessions with internal users who know the collection well and the kind of things that users might be looking for, following a set of instructions to obtain data in a useful format.  
This data is small (20-50 rows per person, per session, with ~10 people taking part), but can be deeply contextualised by talking to the people who took part.

The other major dataset is passively collected while users carry out searches and look through pages of results. We track the query string, the results page number, the works they click on, and whether they're on the Wellcome staff network (we want to be able to distinguish between internal and external users). This data is aggregated under an anonymous session id.  
We can collect lots of this data (100,000s of actions logged so far), but it lacks any contextual information and unpicking a user's motivation for an action is hard.

### Metrics
We're using multiple complementary metrics to measure search relevance:
- **Normalised Discounted Cumulative Gain (NDCG)** is well explained in [this post](https://www.ebayinc.com/stories/blogs/tech/measuring-search-relevance/) by ebay. It relies on explicitly collected data from users who know that the data they give us will be used to measure algorithmic performance. It compares the actual performance of a query-type (the order of a set of rated results) to the _ideal_ performance (the same results, sorted from most to least relevant).
- **Strict, loose, and permissive precision** use the same data as NDCG. For strict precision, the percentage of results rated 4+ is counted. Loose precision counts the percentage of 3+ ratings, and permissive precision counts 2+. Explained in the book [_Search Analytics For Your Site_](https://rosenfeldmedia.com/books/search-analytics-for-your-site/) by Louis Rosenfeld.
- **Click through rate (CTR)** is measured passivley by tracking users' behaviour while they use the search function. This version of CTR is different to the usual definition; we take the ratio of the number of distinct searches to the number of items clicked on for each anonymised session id.
- **Top 5 click through rate (CTR5)** is almost exactly the same as the above, but only counts the clicks on works which appear in the top 5 results.

It's important to note that the metrics we've chosen aren't perfect, and we can't always assume that the candidate with the largest value is best. The results require interpretation alongside the data itself to build a clear picture of what's going on and why, as we'll demonstrate in the discussion of the tests below.  

# Tests
- [read about test 1 here](./test_1.md)
- [read about test 2 here](./test_2.md)