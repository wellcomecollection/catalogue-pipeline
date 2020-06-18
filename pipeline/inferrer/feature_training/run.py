#!/usr/bin/env python3

import boto3
import click

VPC_NAME = "catalogue-172-31-0-0-16"
session = boto3.Session(profile_name="platform-developer")


# Gets the first private subnet of the catalogue VPC
def get_subnet():
    ec2 = session.client("ec2")
    vpcs = ec2.describe_vpcs(Filters=[{"Name": "tag:Name", "Values": [VPC_NAME]}])
    catalogue_vpc_id = vpcs["Vpcs"][0]["VpcId"]
    subnets = ec2.describe_subnets(
        Filters=[
            {"Name": "vpc-id", "Values": [catalogue_vpc_id]},
            {"Name": "tag:Availability", "Values": ["private"]},
        ]
    )
    return subnets["Subnets"][0]["SubnetId"]


def get_kibana_logs_url(task_arn):
    return f"https://logging.wellcomecollection.org/app/kibana#/discover?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-1d,to:now))&_a=(columns:!(_source),filters:!(('$state':(store:appState),meta:(alias:!n,disabled:!f,index:'978cbc80-af0d-11ea-b454-cb894ee8b269',key:ecs_task_arn.keyword,negate:!f,params:(query:'{task_arn}'),type:phrase),query:(match_phrase:(ecs_task_arn.keyword:'{task_arn}')))),index:'978cbc80-af0d-11ea-b454-cb894ee8b269',interval:auto,query:(language:kuery,query:''),sort:!())"


@click.group()
def cli():
    pass


@cli.command()
@click.option(
    "--pipeline-name",
    help="The name of the pipeline (eg catalogue-19700101)",
    required=True,
)
def train_new_model(pipeline_name):
    print("Starting model training task...")
    ecs_client = session.client("ecs")
    res = ecs_client.run_task(
        taskDefinition=f"{pipeline_name}_image_training",
        cluster=pipeline_name,
        count=1,
        launchType="FARGATE",
        networkConfiguration={
            "awsvpcConfiguration": {
                "subnets": [get_subnet()],
                "assignPublicIp": "ENABLED",
            }
        },
        platformVersion="1.4.0",
    )
    print(f"Successfully started training task [{res['tasks'][0]['taskArn']}]")
    print("You can follow the logs at:")
    print(get_kibana_logs_url(pipeline_name))


@cli.command()
@click.option("--from-label", help="The release label to deploy from", default="latest")
@click.option("--to-label", help="The release label to deploy to", required=True)
def deploy_model(from_label, to_label):
    ssm_path_template = "/catalogue_pipeline/config/models/%s/lsh_model"
    ssm_client = session.client("ssm")
    from_ssm_parameter = ssm_client.get_parameter(Name=(ssm_path_template % from_label))
    from_value = from_ssm_parameter["Parameter"]["Value"]

    print(f"Updating {to_label} to {from_value!r}...")
    ssm_client.put_parameter(
        Name=(ssm_path_template % to_label),
        Value=from_value,
        Overwrite=True,
        Type="String",
    )
    print(f"Updated {to_label} to {from_value!r}")


if __name__ == "__main__":
    cli()
