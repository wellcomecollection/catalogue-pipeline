# LSH model training

This service is complementary to the `feature_inferrer` service: it trains the LSH model on VGG embeddings, puts a model artifact in an S3 bucket and updates an SSM parameter with the key of the artifact. This key looks like `lsh_model/<commit_hash>/<timestamp>.pkl`.

## How to use it

#### If there are already vectors in an index

You can start an ECS task that will fetch vectors from the index and train a new model:

```bash
./run.py train-new-model --pipeline-name=catalogue-19700101
```

The `--pipeline-name` option determines the task definition that's run and the cluster it runs on.

#### If you don't have any feature vectors in an ES index

You can use local feature vectors to train the LSH model on your own machine:

```bash
AWS_PROFILE=platform-developer python3 ./train_lsh_locally.py --feature-vector-path=<vectors directory>
```

## What do I do when I've trained a model?

The model trainer will update the SSM parameter labelled `latest`. Pipelines (and hence the inferrer) have release labels like `latest`, `prod`, or `stage`. To start using a new model that's in `latest`, you can run:

```bash
python3 ./run.py deploy-model --to-label=prod
```
This will update the `prod` label, and if a pipeline stack with release label `prod` is now applied, it will use that model.

## How to deploy it

The task definition which gets run by `./run.py train-new-model` is created as part of a pipeline stack and will use an image published by CI (or manually) just like any of the regular services.
