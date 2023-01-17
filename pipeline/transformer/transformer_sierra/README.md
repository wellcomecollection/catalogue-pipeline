# transformer_sierra

The [`SierraLiveDataTransformerTest.scala`](SierraLiveDataTransformerTest.scala.txt) file alongside this README allows you to transform "live" data with your local transformer.
This is useful for debugging and testing new transformer code.

To use it:

1.  Add a dependency on the STS SDK:

    ```scala
    val stsDependencies = Seq(
        "software.amazon.awssdk" % "sts" % "2.11.14"
    )
    ```

    The version of the AWS SDK should match that used by [scala-libs](https://github.com/wellcomecollection/scala-libs/blob/main/project/Dependencies.scala).

2.  Copy the file into the test directory and run it there, and add any assertions or checks you want on the returned work.

We don't copy the file into the project directly because it's not part of the mainline functionality of the Sierra transformer, and we may go long stretches without using it.

It's provided as a starting point for running a local transformer, not code that's guaranteed to be up-to-date and always working.
