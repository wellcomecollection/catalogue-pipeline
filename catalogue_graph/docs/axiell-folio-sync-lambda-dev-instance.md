# Connecting the sync Lambda to the FOLIO dev server

The `axiell_folio_sync` Lambda can write to either FOLIO instance:

| Target | Instance | Reached over |
| --- | --- | --- |
| `prod` (default) | EBSCO SaaS, `api-wellcome.folio.ebsco.com` | public internet |
| `dev` | the `folio-sandbox` EC2 instance, Kong on `:8000` | the catalogue VPC |

The sandbox has no public IP and no inbound ports, so the Lambda has to be attached to the VPC to
reach it. That is gated behind the `folio_dev_target_enabled` local in
`infra/adapters/folio_dev_sandbox.tf`, which is currently `true`. It is set in code because
several people apply this root, and a flag passed on the command line would be dropped by the
next person's apply, taking the hand-filled dev SecureString with it.

## How a run picks its target

Highest precedence first:

1. `folio_target` on the event, either `"prod"` or `"dev"`
2. the `FOLIO_TARGET` environment variable on the Lambda
3. `"prod"`

The two entry points take their default from different places:

- **Scheduled runs** (Axiell adapter, then `axiell.adapter.completed`, then EventBridge, then Step
  Functions) resolve `folio_target` inside the state machine definition, which is baked at apply
  time from `folio_default_target`. These runs never read the Lambda's environment.
- **Direct `lambda invoke`** falls through to `FOLIO_TARGET` on the Lambda, which Terraform sets
  from `folio_default_target`. Naming `folio_target` on the event is the usual way to reach the
  sandbox, and avoids depending on that default.

Each target has its own SSM SecureString, and there is no fallback between them. A `dev` run whose
parameter is missing or incomplete fails instead of writing to production.

## What is deployed

| Where | What |
| --- | --- |
| `infra/adapters/folio_dev_sandbox.tf` | `folio_dev_target_enabled`, the ENI security group, its egress rule, and the output carrying its id |
| `folio-dev-server`, `terraform/main.tf` | `sync_lambda_security_group_id`, and the `:8000` ingress rule on `folio-sandbox-sg` |
| `infra/adapters/main.tf` | `folio_default_target`, and the subnets and security groups passed to the module |
| `modules/axiell_folio_sync/lambda.tf` | `vpc_config` when the flag is set and `null` otherwise, plus `FOLIO_TARGET` and `OKAPI_DEV_SECRET_PARAM` in the environment |
| `modules/axiell_folio_sync/ssm.tf` | `…/okapi_credentials_dev`, seeded with placeholders |
| `modules/axiell_folio_sync/iam.tf` | `ssm:GetParameter` extended to the dev parameter, and `cloudwatch:PutMetricData` scoped to the `catalogue_adapters` namespace the report publishes to |
| `modules/axiell_folio_sync/locals.tf` | `folio_default_target` clamped to `prod` unless `folio_dev_target_enabled` is set, so the two cannot combine into a deployment where every run fails |
| `modules/axiell_folio_sync/state_machine.tf` | `folio_target` and `hard_delete` mapped into the Lambda payload |
| `folio/okapi.py` | `resolve_folio_target`, and `load_okapi_config(target)` choosing the parameter |
| `models.py` | `FolioTarget`, `folio_target` on the event, and the resolved target on the response |
| `report.py` | the resolved target on the run manifest |

The Lambda runs at 2048 MB. Loading the reference-data cache dominates memory use, and measured
peaks range from about 720 MB to 960 MB against both tenants, so 512 MB is not enough to finish a
run. The variation tracks cold against warm invocations more closely than it tracks which tenant
is being read.

## Network path

With `folio_dev_target_enabled` set, the Lambda's ENIs sit in
`catalogue_vpc_delta_private_subnets`. Those are the same subnets as the adapter ECS tasks, and
the same VPC as the sandbox, so traffic to it stays local. Attaching to a VPC removes the Lambda's
default internet egress, and these subnets replace it:

| Hop | Provided by |
| --- | --- |
| Lambda ENI to the sandbox on `:8000` | `172.31.0.0/16 → local`, plus the ingress rule described below |
| SSM, KMS, S3 Tables, CloudWatch metrics, FOLIO SaaS | NAT gateway on the subnets' route table |
| S3 (manifests, Iceberg parquet) | S3 gateway endpoint on the same route table, bypassing NAT |
| CloudWatch Logs | written by the Lambda service, not through the ENI |

### Who owns which security group

The two ends are owned by different repos, and this matters more than it looks.

`folio-sandbox-sg` belongs to
[`wellcomecollection/folio-dev-server`](https://github.com/wellcomecollection/folio-dev-server)
(`terraform/main.tf`, `aws_security_group "folio"`, named `"${var.name}-sg"`). It declares its
ingress with **inline blocks**, which makes that resource the exclusive owner of the group's
ingress: any rule created from another repo is deleted the next time `folio-dev-server` is
applied. So the `:8000` rule lives there too, behind its `sync_lambda_security_group_id`
variable, rather than here.

This repo owns only the sync Lambda's own ENI security group, and publishes its id as the
`axiell_folio_sync_dev_security_group_id` output for that variable to consume.

Keeping it this way has a useful side effect: the adapters root holds no data source pointing at
the sandbox, so a destroyed or rebuilt sandbox cannot break a plan of unrelated adapter changes.

FOLIO's gateway listens on `0.0.0.0:8000` over plain HTTP, so the OKAPI url is
`http://<private-ip>:8000`.

## Enabling it

This takes an apply in each repo, in this order. The sandbox's ingress rule has to reference a
security group that already exists.

1. In `catalogue_graph/infra/adapters`, set `folio_dev_target_enabled = true` in
   `folio_dev_sandbox.tf` through a pull request, apply, then read the ENI security group's id:

   ```bash
   terraform apply
   terraform output -raw axiell_folio_sync_dev_security_group_id
   ```

   While the local is `false`, an apply leaves the Lambda off the VPC and does not create the
   dev SecureString. The ENI security group and its egress rule are kept either way for a
   stable id, and are harmless when detached.

2. In `folio-dev-server/terraform`, open Kong to that group:

   ```bash
   terraform apply -var "sync_lambda_security_group_id=$SG_ID"
   ```

   The ENI security group is not gated on `folio_dev_target_enabled`, so its id is stable
   across toggles. Once this is set it stays correct, and step 2 can be skipped on later
   sessions.
3. Start the sandbox if it is stopped, and note its private IP. It is looked up by tag because
   a rebuild changes both the instance id and the address:

   ```bash
   id=$(aws ec2 describe-instances --region eu-west-1 \
     --filters 'Name=tag:Name,Values=folio-sandbox' \
               'Name=instance-state-name,Values=running,stopped' \
     --query 'Reservations[].Instances[].InstanceId' --output text)
   aws ec2 start-instances --region eu-west-1 --instance-ids "$id"
   aws ec2 wait instance-running --region eu-west-1 --instance-ids "$id"
   aws ec2 describe-instances --region eu-west-1 --instance-ids "$id" \
     --query 'Reservations[].Instances[].PrivateIpAddress' --output text
   ```

   Kong takes a few more minutes to come up after the instance reaches `running`.

4. Fill in the dev SecureString, which Terraform creates holding only placeholders. The url
   uses the IP from step 3, and the password comes from Secrets Manager, so it never has to be
   read or pasted by hand:

   ```bash
   pw=$(aws secretsmanager get-secret-value --region eu-west-1 \
     --secret-id folio-sandbox/diku-admin-password \
     --query SecretString --output text)
   aws ssm put-parameter --region eu-west-1 \
     --name /catalogue_pipeline/axiell-folio-sync/okapi_credentials_dev \
     --type SecureString --overwrite \
     --value "$(jq -nc --arg url "http://<private-ip>:8000" --arg p "$pw" \
       '{url:$url, tenant:"diku", username:"diku_admin", password:$p}')"
   ```

   This has to be redone after a sandbox rebuild, since the url follows the private IP.

5. Invoke the Lambda, naming the target on the event:

   ```bash
   aws lambda invoke --region eu-west-1 \
     --function-name axiell-folio-sync-adapter-lambda \
     --cli-binary-format raw-in-base64-out \
     --payload '{"job_id":"folio-dev-smoke","folio_target":"dev","dry_run":true,"sample_limit":1}' \
     /dev/stdout
   ```

To finish, stop the instance and follow the Rollback steps below.

The response carries `folio_target`, and a successful run logs `folio target resolved` with the
url it chose. Either one confirms which instance was written to. If `folio_target` is missing
from the response, the deployed image predates the field and the run went to prod.

## Credentials

Terraform creates `…/okapi_credentials_dev` with placeholder values and ignores later changes to
it. The real value is written by hand, as in step 4 above, so the sandbox password is never read
into a plan or into `adapters.tfstate`. It has to be rewritten whenever the sandbox is rebuilt,
because the url follows the instance's private IP.

## Known failure modes

| Symptom | Cause |
| --- | --- |
| `ConnectTimeout` on the first FOLIO call | the sandbox is stopped, or Kong has not finished starting. Kong lags the instance reaching `running` by several minutes |
| `ConnectTimeout` while the sandbox is up | the dev SecureString holds a stale url after a rebuild. Rewrite it, as in step 4 of Enabling it |
| A run reaches production despite `folio_target: "dev"` | the deployed image predates the field, and Pydantic drops unknown keys silently. Check whether the `folio target resolved` line appears in the logs |
| `Runtime.OutOfMemory` | `lambda_memory_mb` is below about 1 GB |
| `DependencyViolation` destroying a security group after detaching the Lambda | Lambda deletes its ENIs asynchronously, minutes after `vpc_config` is removed. This is why the ENI security group is never destroyed by a toggle. If you hit it another way, wait for the ENIs to disappear and apply again |

## Rollback

In `catalogue_graph/infra/adapters`, set `folio_dev_target_enabled = false` in
`folio_dev_sandbox.tf` through a pull request and apply. The Lambda drops its ENIs and returns to
the Lambda-managed network, and the dev parameter is destroyed with it, including the credentials
filled in by hand. One apply, no ordering to observe.

The ENI security group is deliberately left in place rather than destroyed. Lambda deletes its
ENIs asynchronously, so destroying the group in the same apply that detaches the function fails
with `DependencyViolation` while those ENIs are still in use. An empty security group costs
nothing, and keeping it means its id stays valid for `folio-dev-server`.

That also makes the sandbox's `:8000` rule harmless to leave configured between sessions: it
allows a security group that nothing is attached to. Clear `sync_lambda_security_group_id` and
apply `folio-dev-server` if you want it gone, but nothing here depends on that.

Neither repo modifies route tables, NAT gateways or endpoints.
