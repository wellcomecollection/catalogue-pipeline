# What is an adapter?

An *adapter* is an application that pulls records from source catalogues and into a catalogue pipeline database.
We have one adapter per source catalogue.

## Why do we have adapters?

*   Each adapter absorbs the complexity of the source catalogue's API, e.g. one catalogue might use a REST API, another uses SOAP XML, another sends SNS notifications.
    The adapter uses this API and copies the data into the catalogue (more specifically, a mix of DynamoDB and S3).

    Downstream code can then read from the catalogue copy of the data, and not worry about how the source catalogue works.

    (This gives adapters their name, compare to [plug adapters](https://en.wikipedia.org/wiki/Adapter).)

*   Adapters isolate the catalogue pipeline from problems in the source catalogues.

    e.g. if CALM is down for upgrades, the pipeline is unaffected because it has a complete copy of the CALM data.

*   Adapters allow us to reprocess data at speed, without impacting the source catalogues.

    If we want to do some batch processing, we can process our copy of the data and run as fast as we like, without sending extra traffic to the source catalogues.
    This reduces the risk of us running an expensive query that accidentally overwhelms an upstream system, and breaks an application that other teams rely on.
