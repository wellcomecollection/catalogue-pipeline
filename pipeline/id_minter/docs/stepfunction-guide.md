# ID Minter Step Function Implementation Guide

## Overview

The ID Minter provides two interfaces for processing source identifiers:

1. **SQS Interface** (existing) - Processes messages from SQS queues and sends results to downstream SQS
2. **Step Function Interface** (new) - Direct invocation for AWS Step Functions workflows

This guide covers the Step Function interface implementation, deployment, and integration.

## Architecture

### Step Function Interface Components

- **`StepFunctionMain`** - Entry point for Step Function Lambda
- **`IdMinterStepFunctionLambda`** - Step Function-specific Lambda handler
- **`StepFunctionMintingRequest/Response`** - Request/response data models
- **Shared Components** - Reuses `MintingRequestProcessor`, `MultiIdMinter`, database, and Elasticsearch components

### Key Differences from SQS Interface

| Aspect | SQS Interface | Step Function Interface |
|--------|---------------|------------------------|
| **Input** | SQS messages with source IDs | Direct JSON request with source identifiers |
| **Output** | SQS messages to downstream queue | Direct JSON response with success/failure indicators |
| **Error Handling** | BatchItemFailure responses | Structured failure objects in response |
| **Configuration** | Includes downstream SQS config | No downstream configuration needed |
| **Use Case** | Asynchronous batch processing | Synchronous Step Function workflows |

## Request/Response Format

### Request Format

```json
{
  "sourceIdentifiers": [
    "sierra-123456",
    "miro-789012",
    "calm-uuid-here"
  ],
  "jobId": "optional-job-identifier"
}
```

**Fields:**
- `sourceIdentifiers` (required): Array of source identifier strings (1-100 items)
- `jobId` (optional): Job identifier for tracking purposes

### Response Format

```json
{
  "successes": [
    "sierra-123456",
    "miro-789012"
  ],
  "failures": [
    {
      "sourceIdentifier": "calm-uuid-here",
      "error": "Failed to mint ID for calm-uuid-here"
    }
  ],
  "jobId": "optional-job-identifier"
}
```

**Fields:**
- `successes`: Array of source identifiers that were successfully processed
- `failures`: Array of failure objects with source identifier and error message
- `jobId`: Echo of the request jobId (if provided)

### Validation Rules

- `sourceIdentifiers` cannot be empty
- `sourceIdentifiers` cannot contain empty or whitespace-only strings
- Maximum 100 source identifiers per request
- Each source identifier must be a valid string

## Local Development

### Building and Running

```bash
# Build the Step Function Lambda
docker compose -f local.docker-compose.yml build stepfunction-lambda

# Run with dependencies (MySQL, Elasticsearch)
docker compose -f local.docker-compose.yml up -d mysql elasticsearch-local
docker compose -f local.docker-compose.yml run --rm --service-ports stepfunction-lambda
```

### Testing Locally

The Step Function Lambda runs on port 9001 when using local docker-compose.

```bash
# Example test request
curl -X POST "http://localhost:9001/2015-03-31/functions/function/invocations" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceIdentifiers": ["sierra-test-123"],
    "jobId": "local-test"
  }'
```

## Deployment

### Docker Build Targets

The Dockerfile provides multiple build targets:

```bash
# Build SQS Lambda (default)
docker build -t id-minter-sqs .

# Build Step Function Lambda
docker build --target stepfunction -t id-minter-stepfunction .

# Build Lambda RIE for local testing
docker build --target lambda_rie -t id-minter-local .
```

### Environment Configuration

The Step Function Lambda uses the same environment variables as the SQS Lambda, except:

**Not Required:**
- `downstream_*` variables (no SQS messaging)
- `use_downstream` variable

**Required:**
- Database configuration (`cluster_url`, `db_*`)
- Elasticsearch configuration (`es_*`)
- Application configuration (`metrics_namespace`)

## AWS Integration

### Step Function State Machine Integration

Example Step Function state definition:

```json
{
  "Comment": "ID Minting Workflow",
  "StartAt": "MintIds",
  "States": {
    "MintIds": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:region:account:function:id-minter-stepfunction",
      "Parameters": {
        "sourceIdentifiers.$": "$.sourceIdentifiers",
        "jobId.$": "$.jobId"
      },
      "ResultPath": "$.mintingResult",
      "Next": "ProcessResults"
    },
    "ProcessResults": {
      "Type": "Choice",
      "Choices": [
        {
          "Variable": "$.mintingResult.failures",
          "IsPresent": true,
          "Next": "HandleFailures"
        }
      ],
      "Default": "Success"
    },
    "HandleFailures": {
      "Type": "Pass",
      "Result": "Some identifiers failed to mint",
      "End": true
    },
    "Success": {
      "Type": "Pass",
      "Result": "All identifiers minted successfully",
      "End": true
    }
  }
}
```

### Error Handling in Step Functions

The Step Function interface returns structured errors rather than throwing exceptions:

```json
{
  "successes": ["sierra-123"],
  "failures": [
    {
      "sourceIdentifier": "invalid-id",
      "error": "Failed to mint ID for invalid-id"
    }
  ],
  "jobId": "job-123"
}
```

Use Step Function Choice states to handle partial failures:

```json
{
  "Type": "Choice",
  "Choices": [
    {
      "Variable": "$.mintingResult.failures[0]",
      "IsPresent": true,
      "Next": "HandlePartialFailure"
    }
  ],
  "Default": "AllSuccess"
}
```

## Performance Considerations

### Batch Size Optimization

- **Small batches (1-10 items)**: ~1-2 seconds processing time
- **Medium batches (10-50 items)**: ~5-10 seconds processing time  
- **Large batches (50-100 items)**: ~15-30 seconds processing time

### Lambda Configuration Recommendations

- **Memory**: 512MB - 1024MB (depending on batch size)
- **Timeout**: 30 seconds (maximum for Step Functions)
- **Concurrent Executions**: Set based on database connection limits

### Database Connection Management

The Step Function Lambda shares the same database connection pool as the SQS Lambda. Consider:

- Database connection limits when running both interfaces
- Connection pool sizing (`max_connections` environment variable)
- Database read replica usage for read-heavy workloads

## Monitoring and Observability

### CloudWatch Metrics

Key metrics to monitor:

- **Duration**: Lambda execution time
- **Errors**: Lambda function errors
- **Throttles**: Lambda throttling events
- **SuccessRate**: Percentage of successful ID minting operations

### Custom Metrics

The Step Function Lambda emits the same custom metrics as the SQS Lambda:

- `id_minter.processing_time`
- `id_minter.batch_size`
- `id_minter.success_count`
- `id_minter.failure_count`

### Logging

Structured logging includes:

- Request correlation IDs
- Batch processing metrics
- Database operation timing
- Elasticsearch indexing results

## Security and IAM

### Required IAM Permissions

The Step Function Lambda requires these permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "rds:DescribeDBInstances"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "es:ESHttpPost",
        "es:ESHttpGet",
        "es:ESHttpPut"
      ],
      "Resource": "arn:aws:es:region:account:domain/elasticsearch-domain/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:region:account:*"
    }
  ]
}
```

### Permissions NOT Required

Unlike the SQS Lambda, the Step Function Lambda does NOT need:

- `sqs:ReceiveMessage`, `sqs:DeleteMessage` (no SQS input)
- `sns:Publish` (no downstream messaging)
- `sqs:SendMessage` (no downstream SQS)

## Migration from SQS to Step Function

### When to Use Each Interface

**Use SQS Interface when:**
- Processing large volumes of messages asynchronously
- Need downstream SQS messaging for other services
- Existing integration with SQS-based workflows
- Batch processing with retry mechanisms

**Use Step Function Interface when:**
- Synchronous processing within Step Function workflows
- Direct response needed for workflow decisions
- Orchestrating complex multi-step processes
- Need precise control over error handling and retries

### Migration Strategy

1. **Assessment Phase**
   - Identify current SQS usage patterns
   - Analyze downstream message consumers
   - Evaluate Step Function workflow requirements

2. **Parallel Deployment**
   - Deploy Step Function Lambda alongside existing SQS Lambda
   - Test Step Function interface with subset of traffic
   - Validate performance and error handling

3. **Gradual Migration**
   - Migrate workflows one at a time
   - Update Step Function state machines to use new interface
   - Monitor performance and error rates

4. **Cleanup**
   - Remove SQS Lambda when no longer needed
   - Update infrastructure and monitoring

### Data Format Migration

No data migration is required - both interfaces use the same:
- Database schema for identifier storage
- Elasticsearch indices for work storage
- Canonical ID generation logic

## Future Implementation Steps

### Infrastructure Automation

**Terraform/CloudFormation:**
- Lambda function definitions
- IAM roles and policies
- CloudWatch alarms and dashboards
- Step Function state machine templates

**Example Terraform:**
```hcl
resource "aws_lambda_function" "id_minter_stepfunction" {
  function_name = "id-minter-stepfunction"
  role         = aws_iam_role.id_minter_stepfunction.arn
  handler      = "weco.pipeline.id_minter.StepFunctionMain::handleRequest"
  runtime      = "java11"
  timeout      = 30
  memory_size  = 1024
  
  environment {
    variables = {
      cluster_url = var.database_host
      es_downstream_host = var.elasticsearch_host
      # ... other environment variables
    }
  }
}
```

### CI/CD Integration

**Build Pipeline:**
- Separate build artifacts for SQS and Step Function Lambdas
- Automated testing for both interfaces
- Deployment automation with rollback capabilities

**Testing Strategy:**
- Integration tests with real AWS services
- Performance testing with various batch sizes
- Chaos engineering for resilience testing

### Advanced Monitoring

**Distributed Tracing:**
- AWS X-Ray integration for request tracing
- Correlation IDs across Step Function workflows
- Performance bottleneck identification

**Advanced Alerting:**
- Anomaly detection for processing times
- Automated scaling based on load patterns
- Proactive error rate monitoring

### Step Function Patterns

**Batch Processing Pattern:**
```json
{
  "Type": "Map",
  "ItemsPath": "$.batches",
  "MaxConcurrency": 5,
  "Iterator": {
    "StartAt": "MintBatch",
    "States": {
      "MintBatch": {
        "Type": "Task",
        "Resource": "arn:aws:lambda:region:account:function:id-minter-stepfunction",
        "End": true
      }
    }
  }
}
```

**Error Retry Pattern:**
```json
{
  "Type": "Task",
  "Resource": "arn:aws:lambda:region:account:function:id-minter-stepfunction",
  "Retry": [
    {
      "ErrorEquals": ["States.TaskFailed"],
      "IntervalSeconds": 2,
      "MaxAttempts": 3,
      "BackoffRate": 2.0
    }
  ],
  "Catch": [
    {
      "ErrorEquals": ["States.ALL"],
      "Next": "HandleError"
    }
  ]
}
```

## Troubleshooting

### Common Issues

**Timeout Errors:**
- Reduce batch size
- Increase Lambda timeout (max 30s for Step Functions)
- Check database connection performance

**Memory Errors:**
- Increase Lambda memory allocation
- Monitor memory usage patterns
- Optimize batch processing logic

**Database Connection Issues:**
- Check connection pool configuration
- Verify database security groups
- Monitor connection count limits

**Elasticsearch Errors:**
- Verify Elasticsearch cluster health
- Check index permissions
- Monitor Elasticsearch connection timeouts

### Debug Logging

Enable debug logging by setting environment variables:
```bash
LOG_LEVEL=DEBUG
SCALA_LOGGING_LEVEL=DEBUG
```

### Health Checks

Test Lambda health with minimal request:
```json
{
  "sourceIdentifiers": ["test-health-check"],
  "jobId": "health-check"
}
```

## Support and Maintenance

### Code Maintenance

- Both SQS and Step Function interfaces share core business logic
- Changes to `MintingRequestProcessor` affect both interfaces
- Database schema changes require coordination
- Elasticsearch mapping updates need testing with both interfaces

### Operational Procedures

- Monitor both interfaces independently
- Separate alerting for SQS vs Step Function errors
- Coordinate deployments to avoid service disruption
- Maintain backward compatibility for existing integrations

For questions or issues, refer to the main [README.md](../README.md) or contact the platform team.