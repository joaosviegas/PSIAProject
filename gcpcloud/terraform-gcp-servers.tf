# GCP compute instances for the Kubernetes cluster.
# https://www.terraform.io/docs/configuration/resources.html
#
# Architecture: 1 master (control plane) + 3 workers (run pods).

###########  Workers (run application pods)  #############
resource "google_compute_instance" "worker" {
    count        = 3
    name         = "worker${count.index+1}"
    machine_type = var.GCP_MACHINE_TYPE
    zone         = var.GCP_ZONE

    boot_disk {
        initialize_params {
            # https://console.cloud.google.com/compute/images
            image = "ubuntu-os-cloud/ubuntu-2204-lts"
            size  = 50  # GB — default 10 GB fills up with Docker images (Ollama alone is ~2 GB)
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

    tags = ["cluster", "worker"]
}

###########  Master (Kubernetes control plane)  #############
resource "google_compute_instance" "master" {
    name         = "master"
    machine_type = var.GCP_MACHINE_TYPE
    zone         = var.GCP_ZONE

    boot_disk {
        initialize_params {
            image = "ubuntu-os-cloud/ubuntu-2204-lts"
            size  = 50
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

    tags = ["cluster", "master"]
}
