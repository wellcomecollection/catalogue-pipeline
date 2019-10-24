module "vhs_miro" {
  source = "./vhs"
  name   = "sourcedata-miro"

  read_principals = ["${concat(
      local.read_principles, 
      list("arn:aws:iam::964279923020:role/palette-api_task_role")
    )}"]
}