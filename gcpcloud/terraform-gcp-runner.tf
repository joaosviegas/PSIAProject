# GitLab CI runner VM - kept separate from the k8s cluster so CI survives a broken/rebuilt cluster.
#
# Docker executor with the host Docker socket mounted: the build stage runs `docker build`
# and the e2e stage runs `docker compose up`, both against the VM's own Docker daemon.
#
# The runner token comes from GitLab > Settings > CI/CD > Runners > New project runner.
# Put it in gcpcloud/runner.auto.tfvars (gitignored), never in this file:
#   GITLAB_RUNNER_TOKEN = "glrt-..."

variable "GITLAB_RUNNER_TOKEN" {
    type      = string
    sensitive = true
}

resource "google_compute_instance" "gitlab_runner" {
    name         = "gitlab-runner"
    # 2 vCPU / 7.5 GB, matches the cluster nodes. Concurrency is 2 below so parallel Maven jobs don't run out of RAM.
    machine_type = "n1-standard-2"
    zone         = var.GCP_ZONE

    boot_disk {
        initialize_params {
            image = "ubuntu-os-cloud/ubuntu-2204-lts"
            size  = 50  # Maven cache + Docker images for every service
        }
    }

    network_interface {
        network = "default"
        access_config {
        }
    }

    metadata = {
        ssh-keys = "ubuntu:${file("/root/.ssh/id_rsa.pub")}"
    }

    # Runs on every boot; install + register are skipped once done.
    metadata_startup_script = <<-EOT
        #!/bin/bash
        set -euo pipefail
        if ! command -v gitlab-runner >/dev/null; then
            curl -fsSL https://get.docker.com | sh
            curl -fsSL https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | bash
            apt-get install -y gitlab-runner
        fi
        if ! grep -q '\[\[runners\]\]' /etc/gitlab-runner/config.toml 2>/dev/null; then
            gitlab-runner register --non-interactive \
                --url "https://gitlab.rnl.tecnico.ulisboa.pt" \
                --token "${var.GITLAB_RUNNER_TOKEN}" \
                --executor docker \
                --docker-image alpine:latest \
                --docker-volumes /var/run/docker.sock:/var/run/docker.sock \
                --docker-volumes /cache
            sed -i 's/^concurrent = .*/concurrent = 2/' /etc/gitlab-runner/config.toml
            systemctl restart gitlab-runner
        fi
    EOT

    tags = ["gitlab-runner"]
}

output "gitlab_runner_ip" {
    value = google_compute_instance.gitlab_runner.network_interface.0.access_config.0.nat_ip
}
