# Connecting the sync Lambda to the FOLIO dev server

The `axiell_folio_sync` Lambda can write to either FOLIO instance:

| Target | Instance | Reached over |
| --- | --- | --- |
| `prod` (default) | EBSCO SaaS, `api-wellcome.folio.ebsco.com` | public internet |
| `dev` | the `folio-sandbox` EC2 instance, Kong on `:8000` | the catalogue VPC |

The sandbox has no public IP and no inbound ports, so the Lambda has to be attached to the VPC to
reach it. That is off by default and gated behind `folio_dev_target_enabled`.

## How a run picks its target

Highest precedence first:

1. `folio_target` on the event, either `"prod"` or `"dev"`
2. the `FOLIO_TARGET` environment variable on the Lambda
3. `"prod"`

The two entry points take their default from different places:

- **Scheduled runs** (Axiell adapter, then `axiell.adapter.completed`, then EventBridge, then Step
  Functions) resolve `folio_target` inside the state machine definition, which is baked at apply
  time from `folio_default_target`. These runs never read the Lambda's environment.
- **Direct `lambda invoke`** falls through to `FOLIO_TARGET`, which
  `scripts/folio_dev_session.sh` sets.

Each target has its own SSM SecureString, and there is no fallback between them. A `dev` run whose
parameter is missing or incomplete fails instead of writing to production.

## What is deployed

| Where | What |
| --- | --- |
| `infra/adapters/folio_dev_sandbox.tf` | `folio_dev_target_enabled`, the `folio-sandbox-sg` lookup, the ENI security group, its egress rule, and the `:8000` ingress rule |
| `infra/adapters/main.tf` | `folio_default_target`, and the subnets and security groups passed to the module |
| `modules/axiell_folio_sync/lambda.tf` | `vpc_config` when the flag is set and `null` otherwise, plus `FOLIO_TARGET` and `OKAPI_DEV_SECRET_PARAM` in the environment |
| `modules/axiell_folio_sync/ssm.tf` | `…/okapi_credentials_dev`, seeded with placeholders |
| `modules/axiell_folio_sync/iam.tf` | `ssm:GetParameter` extended to the dev parameter |
| `modules/axiell_folio_sync/state_machine.tf` | `folio_target` and `hard_delete` mapped into the Lambda payload |
| `folio/okapi.py` | `resolve_folio_target`, and `load_okapi_config(target)` choosing the parameter |
| `models.py` | `FolioTarget`, and `folio_target` on the event |
| `scripts/folio_dev_session.sh` | starts the sandbox, populates the dev SecureString, switches `FOLIO_TARGET` |

The Lambda runs at 2048 MB. Loading the reference-data cache on its own peaks at roughly 870 MB
against the prod tenant and 720 MB against `diku`, so 512 MB is not enough to finish a run.

## Network path

With `folio_dev_target_enabled` set, the Lambda's ENIs sit in
`catalogue_vpc_delta_private_subnets`. Those are the same subnets as the adapter ECS tasks, and
the same VPC as the sandbox, so traffic to it stays local. Attaching to a VPC removes the Lambda's
default internet egress, and these subnets replace it:

| Hop | Provided by |
| --- | --- |
| Lambda ENI to the sandbox on `:8000` | `172.31.0.0/16 → local`, plus the ingress rule below |
| SSM, KMS, S3 Tables, CloudWatch metrics, FOLIO SaaS | NAT gateway on the subnets' route table |
| S3 (manifests, Iceberg parquet) | S3 gateway endpoint on the same route table, bypassing NAT |
| CloudWatch Logs | written by the Lambda service, not through the ENI |

`folio-sandbox-sg` carries exactly one ingress rule, created here: tcp `:8000` from the sync
Lambda's security group. FOLIO's gateway listens on `0.0.0.0:8000` over plain HTTP, so the OKAPI
url is `http://<private-ip>:8000`.

## Enabling it

1. Set `folio_dev_target_enabled = true` in `folio_dev_sandbox.tf` and apply.
2. Run `scripts/folio_dev_session.sh on`. That starts the sandbox if it is stopped, writes the dev
   SecureString using the instance's live private IP and the
   `folio-sandbox/diku-admin-password` Secrets Manager entry, and sets `FOLIO_TARGET=dev`.
3. Invoke the Lambda directly:

   ```bash
   aws lambda invoke --region eu-west-1 \
     --function-name axiell-folio-sync-adapter-lambda \
     --cli-binary-format raw-in-base64-out \
     --payload '{"job_id":"folio-dev-smoke","dry_run":true,"sample_limit":1}' \
     /dev/stdout
   ```

`scripts/folio_dev_session.sh off` reverses steps 2 and 3. `status` reports the sandbox state and
the current target.

A successful run logs `folio target resolved` along with the url it chose. That is the quickest
way to confirm which instance was written to.

## Credentials

Terraform creates `…/okapi_credentials_dev` with placeholder values and ignores later changes to
it. The real value is written outside Terraform by `folio_dev_session.sh`, so the sandbox password
is never read into a plan or into `adapters.tfstate`. The script rewrites the parameter on every
`on`, because the url follows the instance's private IP and that changes whenever the sandbox is
rebuilt.

## Known failure modes

| Symptom | Cause |
| --- | --- |
| `ConnectTimeout` on the first FOLIO call | the sandbox is stopped, or Kong has not finished starting. Kong lags the instance reaching `running` by several minutes |
| `ConnectTimeout` while the sandbox is up | the dev SecureString holds a stale url after a rebuild. Rerun `folio_dev_session.sh on` |
| A run reaches production despite `folio_target: "dev"` | the deployed image predates the field, and Pydantic drops unknown keys silently. Check whether the `folio target resolved` line appears in the logs |
| `Runtime.OutOfMemory` | `lambda_memory_mb` is below about 1 GB |

## Rollback

Set `folio_dev_target_enabled = false` and apply. The Lambda drops its ENIs and returns to the
Lambda-managed network, and the security groups and the dev parameter are destroyed with it. This
configuration does not modify route tables, NAT gateways or endpoints.
