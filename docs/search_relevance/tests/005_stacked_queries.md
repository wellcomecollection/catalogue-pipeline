# Test 5 - Scoring Tiers

## Candidates

Two candidates were compared, one control (AND query from [test 4](./004_AND_or_OR.md)), and one which stacked a loose, generic query with a set of much more constrained and highly boosted queries. [\#246](https://github.com/wellcometrust/catalogue/pull/246)

By layering up the queries from a low-precision, high-recall generic query with no boost, to a highly boosted set of precise queries on a specific set of fields, we're able to tune the precision and recall of our queries and match our queries directly to user intentions. We can also continuously fine-tune these queries as more intentions/expectations are added.

Here, we're stacking a base query with two equally weighted `AND` queries across subject and genre, followed by an even more heavily weighted `OR` query on the title. The code itself can be seen [here](https://github.com/wellcometrust/catalogue/blob/39f8289dac0e7f0bf23bfe9341aa50ac67131b24/api/api/src/main/scala/uk/ac/wellcome/platform/api/services/ElasticsearchQueryBuilder.scala)

[![scoring tiers](https://user-images.githubusercontent.com/11006680/70548661-db6c1b80-1b6a-11ea-8247-7a1358451c06.png)](https://user-images.githubusercontent.com/11006680/70548661-db6c1b80-1b6a-11ea-8247-7a1358451c06.png)

## Results

A bug in the deployment of the query meant that all trafic was directed to the new query, and none to the AND query.

We're haven't observed a drop in any of the most significant metrics like clicks per search over the first few days of the candidate's release:

|  | AND query | scoring tiers |
| :--- | :--- | :--- |
| first page only | 0.235 | 0.234 |
| beyond first page | 0.557 | 0.552 |

While we don't have a parallel dataset to check the candidate against, we can look at the data we have from the and the data from the previous week of AND query to produce the following results, showing no significant quantitative difference in the results:

 [![test click distribution](https://user-images.githubusercontent.com/11006680/70550362-8978c500-1b6d-11ea-84e2-e544c69f00df.png)](https://user-images.githubusercontent.com/11006680/70550362-8978c500-1b6d-11ea-84e2-e544c69f00df.png)

Subjectively, this query seems _significantly_ better than the results from the AND query - we're not seeing so many queries returning 0 results, precision seems high \(the top of the list of results seem intuitively to be the most relevant\), and we're matching enough terms that the recall also seems high. Recall here is driven by the generic base query which we may want to continue to tune to return fewer results in future, perhaps by setting some value of `minimum_should_match`.

As an example of improvements we're seeing - `'everest chest'` now returns the expected results at the top of the list, followed by pictures of Everest, followed by pictures of other chests

 [![everest chest](https://user-images.githubusercontent.com/11006680/70550811-4539f480-1b6e-11ea-89d5-bffdbbe5936f.png)](https://user-images.githubusercontent.com/11006680/70550811-4539f480-1b6e-11ea-89d5-bffdbbe5936f.png)

## Conclusions

We're going forward with the scoring tiers query, and will fine tune our ordering of fields and intentions etc in the next test, probably adding n-grams to a few of the fields to increase precision and shrink recall slightly.

