# Terraform outputs — printed after "terraform apply".
# Use these IPs to fill in gcphosts and /etc/hosts.

output "master" {
    value = join(" ", google_compute_instance.master.*.network_interface.0.access_config.0.nat_ip)
}

output "master_ssh" {
    value = google_compute_instance.master.self_link
}

output "worker_IPs" {
    value = formatlist("%s = %s", google_compute_instance.worker[*].name, google_compute_instance.worker[*].network_interface.0.access_config.0.nat_ip)
}
