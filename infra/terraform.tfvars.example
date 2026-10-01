# Copy to terraform.tfvars and adjust. terraform.tfvars is gitignored - never commit real
# values if you consider any of them sensitive (none of these are secrets by default, but
# your org's policy may differ).

aws_region         = "us-east-1"
project_name       = "bookstore"
pp_desired_count   = 0
prod_desired_count = 0

github_org  = "AlonMamia"
github_repo = "bookstore"
# Numeric owner/repo IDs for GitHub's default immutable OIDC subject format - see
# github_org_id/github_repo_id descriptions in variables.tf. Verify against
# `gh api repos/<org>/<repo>/actions/oidc/customization/sub` before relying on defaults.
github_org_id  = "95974813"
github_repo_id = "1370006649"

# No domain yet: leave certificate_arn empty and the ALB serves HTTP only. Once you own a
# domain, request/validate an ACM certificate for it, set certificate_arn, set the two
# hostnames below to match, and re-apply - the HTTPS listener + redirect turn on with no
# other changes.
certificate_arn = ""
prod_hostname    = "api.bookstore.example"
pp_hostname      = "pp-api.bookstore.example"

prod_cors_allowed_origins = "https://bookstore.example"
pp_cors_allowed_origins   = "https://pp.bookstore.example"

# Keep the RDS instance protected until you're sure you want to allow destroy.
rds_deletion_protection = true
rds_skip_final_snapshot = false
