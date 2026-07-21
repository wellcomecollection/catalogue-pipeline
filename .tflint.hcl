config {
  call_module_type = "none"
}

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

plugin "aws" {
  enabled = true
  version = "0.48.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}

# Advisory check for Terraform naming conventions. Under our current CI
# threshold (--minimum-failure-severity=error), this rule's Notice-level
# findings are reported in logs but do not fail the build.
rule "terraform_naming_convention" {
  enabled = true
}
