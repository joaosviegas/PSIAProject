# Terraform GCP provider configuration.
# https://www.terraform.io/docs/providers/google/index.html
#
# CHANGE THIS: replace the credentials filename with your GCP service account
# JSON key file. Place that file in this directory (gcpcloud/).
# Example: if your file is called "my-project-key.json", set:
#   credentials = file("my-project-key.json")

provider "google" {
    credentials = file("YOUR-GCP-KEY.json")
    project     = var.GCP_PROJECT_ID
    zone        = var.GCP_ZONE
}
