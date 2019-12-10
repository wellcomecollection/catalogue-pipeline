# update\_api\_docs

This task publishes a new Swagger definition to our documentation, which is hosted on [Spotlight](https://stoplight.io/).

It performs an [Import](https://help.stoplight.io/api-v1/versions/import) and a [Publish](https://help.stoplight.io/api-v1/versions/publish), which requires the documentation to have been published at least once before.

## Usage

The task takes three parameters:

* `VERSION_ID` -- the [version identifier](https://help.stoplight.io/api-v1/versions/working-with-versions) for the docs
* `API_SECRET` -- your API secret, as provided by [the Stoplight API](https://help.stoplight.io/api-v1/api-introduction/authentication).
* `SWAGGER_URL` -- the URL of the Swagger file to use.

  This defaults to [https://api.wellcomecollection.org/catalogue/v1/swagger.json](https://api.wellcomecollection.org/catalogue/v1/swagger.json).

You can invoke the task as follows:

```text
aws ecs run-task --cluster=services_cluster \
     --task-definition=update_api_docs_task_definition \
     --overrides="{
       \"containerOverrides\": [
         {
           \"name\": \"app\",
           \"environment\": [
           {\"name\": \"VERSION_ID\", \"value\": \"$VERSION_ID\"},
           {\"name\": \"API_SECRET\", \"value\": \"$API_SECRET\"},
           {\"name\": \"SWAGGER_URL\", \"value\": \"$SWAGGER_URL\"}
         ]
       }
     ]
   }"
```

