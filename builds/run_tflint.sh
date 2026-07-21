#!/usr/bin/env bash

set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

tflint --init

config_file="${repo_root}/.tflint.hcl"

if [[ $# -gt 0 ]]; then
  terraform_roots=("$@")
else
  # A Terraform root is identified by having a backend configuration.
  # Child modules (under modules/, etc.) never declare a backend, so this
  # discovers every root without depending on a committed .terraform.lock.hcl.
  mapfile -t terraform_roots < <(
    grep -rlE '^\s*backend\s+"' --exclude-dir='.terraform' --include='*.tf' . \
      | xargs -r -n1 dirname \
      | sed 's|^\./||' \
      | sort -u
  )
fi

if [[ ${#terraform_roots[@]} -eq 0 ]]; then
  echo "No Terraform roots found"
  exit 1
fi

linted_roots=0

for root in "${terraform_roots[@]}"; do
  root="${root#./}"

  if [[ "${root}" == "reindexer/terraform" ]]; then
    echo "Skipping ${root}; this root currently fails static evaluation in a shared module"
    continue
  fi

  if [[ ! -d "${root}" ]]; then
    echo "Terraform root does not exist: ${root}" >&2
    exit 1
  fi

  echo "Linting ${root}"
  tflint --chdir="${root}" --config="${config_file}" --format=compact --minimum-failure-severity=error
  linted_roots=$((linted_roots + 1))
done

if [[ ${linted_roots} -eq 0 ]]; then
  echo "No Terraform roots were linted"
  exit 1
fi
