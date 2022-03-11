If you are new to the catalogue pipeline the [GitBook documentation](https://docs.wellcomecollection.org/catalogue-pipeline) is a good place to start.

## Developing

Information for developers working on the catalogue-pipeline.

### Deploying

We deploy catalogue-pipeline services using the [weco-deploy](https://github.com/wellcomecollection/weco-deploy) tool.

### Things you might want to do

#### Reindexing

If the [internal_model](../common/internal_model) has been changed you will want to update the information stored by the pipeline to match that model.

A reindex operation runs the source data from the [adapters](adapters/README.md) through the pipeline causing it to be re-transformed / matched & merged as appropriate.

If you want to perform a reindex, follow the instructions in [REINDEXING.md](../REINDEXING.md).

#### Scripts

Generally small things you might want to do irregularly involving the
API & data are in [/scripts](../scripts)

### Problems you might have

* **Stack overflow from scalac \(in IntelliJ\) when building projects**:

  Go to `Settings > Build, Execution, Deployment > Compiler` and change
  `Build process heap size (Mbytes)` to something large, eg 2048.

* **Pulling docker containers from ECR**

  You'll need to log into ECR before local docker can pull from there:

  ```bash
  aws ecr get-login-password --region eu-west-1 --profile platform-dev | \
  docker login --username AWS --password-stdin 760097843905.dkr.ecr.eu-west-1.amazonaws.com
  ```
