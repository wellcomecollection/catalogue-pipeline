# Stress testing script

This script runs `/works` and `/works/{id}` queries with a selection of query strings and work IDs, at a rate that is similar to a high-ish level of usage of the API.

It uses [artillery](https://artillery.io/) and can be run (after a `yarn` to install the artillery dependency) with:
```
yarn start
```
The behaviour is defined in `normal_usage.yml`, the IDs it uses are in `ids.csv`, and the queries are in `queries.csv`.

