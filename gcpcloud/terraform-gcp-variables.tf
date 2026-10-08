# Variables used by all other terraform files.
# https://www.terraform.io/docs/configuration/variables.html
#
# CHANGE THIS: set your GCP project ID.
# Find it in the GCP Console: click the project name at the top dropdown
# and copy the ID (not the display name).

variable "GCP_PROJECT_ID" {
    default = "YOUR-GCP-PROJECT-ID"
}

# Machine type for all nodes (master + workers).
# https://cloud.google.com/compute/docs/machine-types
# https://cloud.google.com/compute/pricing#machinetype
#
# n1-standard-2 gives 7.5 GB RAM per node — needed because we run
# 4 Java services (~512 MB each) + Postgres + Redis + Ollama.
# If budget is tight, try n1-standard-1 (3.75 GB) but Ollama may not fit.
variable "GCP_MACHINE_TYPE" {
    default = "n1-standard-2"
}

# Region and zone for the deployment.
# https://cloud.google.com/compute/docs/regions-zones
# If a zone runs out of resources, try another one (e.g. europe-west1-b).
variable "GCP_ZONE" {
    default = "europe-west1-c"
}

# CHANGE THIS: your Docker Hub username, used in K8s manifests.
# After "docker login" and pushing images, this is the prefix for your images.
# Example: if you pushed "johndoe/g37-auth-service", set this to "johndoe".
variable "DOCKER_HUB_USER" {
    default = "joaosviegas"
}
