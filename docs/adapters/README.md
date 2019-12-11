# Adapters

An _adapter_ is an application that pulls records from an external data source into a DynamoDB table. These typically run on a fixed schedule, e.g. once a day. Because adapters pull in data and then sit idle, we don't run them continuously.

