resource "aws_ssm_parameter" "axiell_collections_oai_api_token" {
  name        = "/catalogue_pipeline/axiell_collections/oai_api_token"
  description = "The API token for the Axiell Collections OAI-PMH endpoint"
  type        = "SecureString"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "axiell_collections_oai_api_url" {
  name        = "/catalogue_pipeline/axiell_collections/oai_api_url"
  description = "The URL for the Axiell Collections OAI-PMH endpoint"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}


resource "aws_ssm_parameter" "folio_oai_api_token" {
  name        = "/catalogue_pipeline/folio/oai_api_token"
  description = "The API token for the FOLIO OAI-PMH endpoint"
  type        = "SecureString"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "folio_oai_api_url" {
  name        = "/catalogue_pipeline/folio/oai_api_url"
  description = "The URL for the FOLIO OAI-PMH endpoint"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}


resource "aws_ssm_parameter" "ebsco_adapter_ftp_server" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_server"
  description = "The FTP server to connect to"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_ftp_username" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_username"
  description = "The username to connect to the FTP server"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_ftp_password" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_password"
  description = "The password to connect to the FTP server"
  type        = "SecureString"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_ftp_remote_dir" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_remote_dir"
  description = "The remote directory to connect to on the FTP server"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}