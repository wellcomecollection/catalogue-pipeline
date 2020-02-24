# Test 6 - English tokeniser and Contributors

## Candidates

A variant of the scoring tiers query introduced in Test 5 was tested. The variant adds an english analyser \(to capture peculiarities of apostrophes in queries like `gray's anatomy`\), and includes contributors in the list of queried fields.

![rough form of a scoring tiers query](../../.gitbook/assets/untitled_drawing.png)

## Results

The test ran for three weeks over the Christmas break. It's worth bearing in mind that the audience we're serving is likely to be less discerning and research-focused if our researcher audience are also less active over these weeks, but the size of the dataset \(~9,000 sessions, 100,000 events\) should still give us reliable indication of goodness.

**Click through rate**

|  | default scoring tiers query | variant |
| :--- | :--- | :--- |
| first page only | 0.238 | 0.220 |
| beyond first page | 0.564 | 0.533 |

**Click distribution**

![download-1](https://user-images.githubusercontent.com/11006680/71817901-408a3280-307f-11ea-95ee-f8aaea9e2f56.png)

## Conclusions

The data indicates that the new variant query performs worse than the default, particularly at the top end of the list of results \(where worse performance is indicated by fewer clicks\).

The difference isn't substantial, and both queries seem to perform better than some of our previous candidates. However, we should still step back and evaluate why performance is lower before proposing a new set of changes.

