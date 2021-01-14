# Catalogue Critical Infrastructure

Contains critical infrastructure for the catalogue services

## Running terraform

In order to run terraform the Elastic Cloud terraform provider requires that the EC_API_KEY environment variable be set.

To retrieve the API key you can run 

```
aws secretsmanager get-secret-value \
    --secret-id elastic_cloud/api_key \
    --profile platform \
    --output text \
    --query 'SecretString'
```

Where the AWS profile used gives you read access to secrets in the platform account.
