terraform {
  backend "s3" {
    bucket       = "alon-bookstore-tfstate-947931884059"
    key          = "bookstore/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
