# Firewall rules for the Kubernetes cluster.
#
# Opens the ports needed by:
# - Kubernetes API (6443) and Calico networking (179, IPIP)
# - NodePort range (30000-32767) so we can expose our app services
# - HTTP/HTTPS (80, 443) for direct access

resource "google_compute_firewall" "cluster_rules" {
    name    = "cluster"
    network = "default"

    allow {
        protocol = "tcp"
        ports = [
            "80", "443",            # HTTP / HTTPS
            "6443",                 # Kubernetes API server
            "179",                  # Calico BGP
            "30000-32767",          # NodePort range (K8s services exposed externally)
        ]
    }

    # Calico IP-in-IP encapsulation (protocol 4)
    # ref: https://docs.tigera.io/calico/latest/getting-started/kubernetes/requirements
    allow {
        protocol = "ipip"
    }

    source_ranges = ["0.0.0.0/0"]
    target_tags   = ["cluster"]
}
