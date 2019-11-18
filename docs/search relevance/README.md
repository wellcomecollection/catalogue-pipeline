# Search relevance

Our current setup for enabling discovery of material on wellcomecollection.org/works is pretty basic. Since going live, api.wellcomecollection.org has used the default elasticsearch `simple_query_string` query type, and we haven't made any significant index-time enhancements (like indexing n-gram tokens) to the underlying data.

The single point of interaction (the search box) and often hundreds of pages of results to sift through leaves users frustrated, feeling a lack of control over the interface and a weakening trust in the system as a whole.

## Baby steps

Instead of working for months on a complete overhaul of the search functionality and putting it in front of users it in one enormous, jarring release, we're making changes incrementally and measuring the effect on performance as we go.

Query-side changes only affect the requests that are sent to our elasticsearch index, rather than the data stored in the index itself. We can make lots of query-side changes without much disruption to the rest of the platform, so we've started there. That said, we do intend to come back to test some index-side changes soon.

## Measurement

We're taking a [data informed](https://stacks.wellcomecollection.org/data-informed-not-data-driven-13377c77d198) approach to change at Wellcome Collection; if we're going to make changes to the the product they should be well motivated and well measured throughout their implementation.

### What are we measuring here?

In broad terms, we want to produce a number which tells us whether a candidate change to the search system makes the results more or less relevant to the person searching.

There's a lot of interesting discussion to be had about different search modalities, precision vs serendipity, linked open data & knowledge graphs, and existential questions about what relevance even _is_, dude, but the broad definition above is a useful, concise statement to revert back to.

### Explicit vs implicit measurement

We're collecting data in two distinct ways:

- **Implicit data collection:** users know that their data might be used to understand aggregate user behaviour, but their behaviour on the site is driven by another motive.

The larger of the two major datasets we are using is passively collected while users carry out searches and look through pages of results.

We track:
* The search query parameters, such as the search terms and page number
* The works they click on and its position in the result set
* Whether they're on the Wellcome staff network as we want to be able to distinguish between internal and external users
* The toggles and A/B tests that are enabled or disabled

This data is aggregated under an anonymous session id.

We can collect lots of this data (100,000s of actions logged so far), but it lacks any contextual information and unpicking a user's motivation for an action is hard.

- **Explicit data collection:** users are told that they are part of an experiment, and providing us with information is their primary motive.

Some of the data we've used to measure search relevance is collected in sessions with internal users who know the collection well and the kind of things that people / researchers might be looking for, following a set of instructions to obtain data in a useful format.

This data is small (20-50 rows per person, per session, with ~10 people taking part), but can be deeply contextualised by talking to the people who took part.

We're now ready to start collecting explicit feedback through an [opt-in mechanism on wellcomecollection.org/works](https://github.com/wellcometrust/wellcomecollection.org/issues/4411), with all users given the option to rate the relevance of individual results in relation to their query.

The resulting data matches the format of the small-scale research described above, but can be collected at a much faster pace with less use of an analyst's time. The increased sample size and breadth of subjects evaluated should also give us more confidence in the 'goodness' of the changes we're making to our search algorithms.

### Metrics

We're using multiple complementary metrics to measure search relevance:

#### Explicit

- **Normalised Discounted Cumulative Gain (NDCG)** is well explained in [this post](https://www.ebayinc.com/stories/blogs/tech/measuring-search-relevance/) by ebay. It relies on explicitly collected data from users who know that the data they give us will be used to measure algorithmic performance. It compares the actual performance of a query-type (the order of a set of rated results) to the _ideal_ performance (the same results, sorted from most to least relevant according to the user's rating).
- **Strict, loose, and permissive precision** use the same data as NDCG. For strict precision, the percentage of results rated 4+ is counted. Loose precision counts the percentage of 3+ ratings, and permissive precision counts 2+. Explained in the book [_Search Analytics For Your Site_](https://rosenfeldmedia.com/books/search-analytics-for-your-site/) by Louis Rosenfeld.

#### Implicit

Among many others, we're looking at:

- **Click through rate (CTR)** is measured passively by tracking users' behaviour while they use the search function. This version of CTR is different to the usual definition; we take the ratio of the number of distinct searches to the number of items clicked on for each anonymised session id.
- **Top n click through rate (CTR5)** is almost exactly the same as the above, but only counts the clicks on works which appear in the top n results.
- **Click distribution curve fitting** uses plots of the distribution of clicks on the first page of results. This distribution tends to follow a regular exponentially decaying shape, but search variants produce slightly different behaviour. In the simplest case of motivation for a test (wanting to generate more clicks at the top of the set of results), observing differences in the graphs over a sufficient window of time will reveal which variant is performing better. This can also be quantified by fitting a line of the form `y = (a * e^(-b * x)) + c` to the curves and comparing the values of `b`. A lower `b` corresponds to a sharper elbow in the decay, and therefore a steeper concentration of clicks towards the top of the result set.

It's important to note that the metrics we're using aren't perfect, and we can't always assume that the candidate with the biggest number is best. The results require interpretation alongside the data itself to build a clear picture of what's going on and why, as we'll demonstrate in the discussion of the tests below.

# Tests

- [read about test 1 here](./test_1.md)
- [read about test 2 here](./test_2.md)
- [read about test 3 here](./test_3.md)
- [read about test 4 here](./test_4.md)
- [read about test 5 here](./test_5.md)
