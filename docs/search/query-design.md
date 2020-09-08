# Query design

## Query design

Search algorithms use different tools or "queries" to return results. These tools can be combined and ordered in relation to people's expectations and priorities. For ease of reference, below is a glossary of tools used. Combinations of tools are indicated in the Guide to Collections Queries.

For clarity, the term, “search” is used to refer to text input by users in the search bar. Text input can consist of one or more words or “tokens.” “Search result order” refers to the way in which works are listed when displaying results.

## Boosting

Items with fields that match query tokens are boosted by attributing numeric values or scores. Search results are sorted by scores in descending order.

## Bool boosted

Items with fields that match query tokens are boosted by attributing higher numeric values or scores than other fields with matched tokens. The bool aspect of this tool is whether a field is being boosted more than other fields. Search results are sorted by scores in descending order.

## Constant score

Items with fields that match query tokens are attributed fixed numeric values or scores. Search results are sorted by scores in descending order.

## Compound queries

Queries containing more than one word or, "token."

## TF/IDF

Means Term Frequency/Inverse Document Frequency. Scores attributed to tokens are based on the frequency in the Catalogue combined with the frequency within an item so that common words such as, “the” are assigned lower values than less commonly-occuring words such as, “brain.” Search results are sorted by TD/IDF scores in descending order.

## Minimum should match

Search results only contain items whose fields match a specified minimum percentage of tokens.

## And

Requires all tokens present in the query to be present in the matching field. This is a much more restrictive strategy and significantly reduces room for user error and serendipitous discovery but may increase overall trust in search.

## Or

The presence of any of the tokens used in a query in the matching field makes it a valid result for inclusion. This introduces a lot of noise into the list of results due to being overly permissive.

## Tiered scoring

Scores for different types of matching are adjusted to increase the power of certain queries in terms of search results ranking. This allows us to balance precision against recall volume requirements.

## Multi match

Searches containing more than one token are matched to fields as phrases representing all possible orders of tokens.

## Phraser beam

Searches containing more than one token are matched to fields as phrases but the degree to which tokens can be reordered is specified. This is a refinement of multi match.

## Stemming

To control for variation in queries, prefixes and suffixes are removed so that only the root word is matched against fields \(eg removal of endings “ly,” “ed” and “ing.”\)

## Stripping

To control for variation in punctuation in queries \(eg “gray's anatomy,” vs “grays anatomy”\), all punctuation is removed before matching.

## Shingling

Users add words to a search in order make it more specific. To use this information fully, instead of searching for each word independently of the others and returning the works in common, the minimum and maximum number of search words to be matched can be specified.

