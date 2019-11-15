# Test 4

## Candidates

Two candidates were compared, one control, and one switching the default operator between query tokens from `OR` to `AND`.

**OR**

Current default - the presence of _any_ of the query tokens in a document makes it a valid result for inclusion. We suspect that this is overly permissive, introducing a lot of noise into the list of results and lowering trust.

**AND**

_All_ tokens present in the query must also be present in the returned documents. This is a much more restrictive scheme, and should only return results that are exactly what the user is asking for. Significantly reduces room for user error and serendipitous discovery, but probably increases overall trust in search. The optimal arrangement is probably a combination of the two, but we want to test whether we're moving in the right direction.

## Results

As discussed in [https://github.com/wellcometrust/platform/issues/4010](#4010), we saw an increase in clicks towards the top of the distribution while maintaining overall click through rate further down. This is exactly what we'd hope to see. The result tracks with both 'discerning' and 'non-discerning' users (depending on whether they decide to look beyond the first page of results). Good indicator that being restrictive at the top end of the results list makes a significant difference.

![image](https://user-images.githubusercontent.com/11006680/68960502-2db65880-07c8-11ea-8aa0-83aaa9988325.png)

## Conclusions

Making the switch from OR to AND as default operator, and beginning a new test which we hope will maintain the restrictive control over the top results while getting a bit more permissive lower down.
