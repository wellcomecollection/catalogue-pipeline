# AWS Lambda functions

This project uses AWS lambda functions for some services where appropriate [using the Python runtime environment](https://docs.aws.amazon.com/lambda/latest/dg/lambda-python.html).

Some examples of project directories that contain Python lambdas are:

- [CALM Adapter, calm_deletion_checker](../../calm_adapter/calm_deletion_check_initiator/)
- [EBSCO Adapter](../../ebsco_adapter/ebsco_adapter/)

## Developing 

In order to work with Python lambdas we use the following conventions.

Working with Python locally requires us to manage which version of Python we are using, and the specific dependencies for a project using [virtual environments](https://docs.python.org/3/library/venv.html).

### Python versions

We use `pyenv` to manage Python versions locally, on OSX you can install this using `brew`.

```console
brew install pyenv
```

You'll need to setup your environment by [following these instructions](https://github.com/pyenv/pyenv?tab=readme-ov-file#set-up-your-shell-environment-for-pyenv). 

At the moment we use Python 3.6 locally, so you will need to install and switch to this version when developing:

```console
pyenv install 3.6
pyenv shell 3.6
```

### Virtual environments

In order to manage dependencies for projects when working locally you can make use of Python virual environments in your project folders. In order to create and activate a virtual environment you can run the following _after_ you have switched to the correct Python version:

```console
python3 -m venv env # Only needed the first time (env folder is in .gitignore)
source env/bin/activate
```

You can then install project dependencies by running:

```console
pip install -r test_requirements.txt #
```

You should now be able to run your lambda locally with:

```console
python main.py # You may need to set the appropriate environment variables to pass permissions and configuration.
```

### Running tests

We use [`tox`](https://tox.wiki/en/4.13.0/) and [`pytest`](https://docs.pytest.org/en/8.0.x/) to manage running tests, and you should find a `tox.ini` above your project directory.

To run tests from the project directory, ensure you have the correct python version and environment set up then run:

```console
tox
```

### Adding dependencies

We use `pip-compile` to generate transitive dependencies in a []`requirement.txt` file from direct dependencies in a `requirements.in` file](https://stackoverflow.com/questions/66751657/what-does-pip-compile-do-what-is-its-use-how-do-i-maintain-the-contents-of-my).

New dependencies should be added to `requirements.in` and then the following commands can be run to update transitive dependencies. Ensure you have the correct python version and environment selected then:

```console
pip install pip-tools # Only needed the first time to install pip-compile
pip-compile
pip-compile test_requirement.in # updates test_requirement.txt, incorporating requirements.txt
```

## Deployment

Our continuous integration environment is configured to build, upload and deploy AWS Lambda functions but this can also be done locally.

In terraform we use the [`wellcomecollection/terraform-aws-lambda` module](https://github.com/wellcomecollection/terraform-aws-lambda) to provide a template for our lamdbas that provides some default configuration like [forwarding Lambda logs to Elasticsearch](https://github.com/wellcomecollection/elasticsearch-log-forwarder). This module expects you to upload zipped pacakages to S3 and then update the Lambda by referencing a newly uploaded package to deploy code changes.

This repository has two scripts to help with this process:

### `./builds/publish_lambda_zip.py`

This script downloads all the dependencies specified by requirements.txt, and packages them alongside your application code in a `.zip` file in `.lambda_zip` at the root of the repository. It then uploads them to S3 in `s3://wellcomecollection-platform-infra/lambdas/project_name`. The script requires AWS credentials. You can run the script locally (ensure you have selected the Python 3.6 env using pyenv):

```console
# Get AWS credentials
aws-azure-login 
# Switch to the correct python version
pyenv shell 3.6 
# Build and publish project, specify location of src folder for your project
AWS_PROFILE=platform-developer ./builds/deploy_lambda_zip.sh ebsco_adapter/ebsco_adapter
```

### `./builds/deploy_lambda_zip.sh`

This script updates a Lambda function you specify from a zip file you've uploaded to S3:

```console
# Get AWS credentials
aws-azure-login 
# Build and publish project, specify project name and name of the target lambda to update
AWS_PROFILE=platform-developer ./builds/publish_lambda_zip.py ebsco_adapter/ebsco_adapter ebsco-adapter-ftp
```
