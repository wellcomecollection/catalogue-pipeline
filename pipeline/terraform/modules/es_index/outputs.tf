output "read" {
  value = {
    read = {
      indices = [
        {
          names      = "${var.name}*"
          privileges = ["read"]
        }
      ]
    }
  }
}

output "write" {
  value = {
    write = {
      indices = [
        {
          names      = "${var.name}*"
          privileges = ["all"]
        }
      ]
    }
  }
}

output "name" {
  value = elasticstack_elasticsearch_index.the_index.name
}
