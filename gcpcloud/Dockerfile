# Management node container.
# Has terraform, ansible, gcloud, kubectl — everything needed to provision
# and configure the GCP infrastructure from inside a container.
# Identical to the lab4 mgmt container.
FROM ubuntu:latest

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update

RUN apt-get install -y supervisor openssh-server iputils-ping unzip wget

RUN apt-get -y install libssl-dev libffi-dev gnupg python3-dev python3-pip snapd

# Ansible
RUN apt install -y software-properties-common && \
    apt-add-repository -y -u ppa:ansible/ansible && \
    apt-get update && \
    apt-get install -y ansible

# Terraform
RUN wget -O- https://apt.releases.hashicorp.com/gpg | gpg --dearmor | tee /usr/share/keyrings/hashicorp-archive-keyring.gpg > /dev/null
RUN echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(grep -oP '(?<=UBUNTU_CODENAME=).*' /etc/os-release || lsb_release -cs) main" | tee /etc/apt/sources.list.d/hashicorp.list
RUN apt-get update && \
    apt-get install -y terraform

RUN apt-get -y install graphviz

# Google Cloud SDK + kubectl
RUN echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
RUN wget -O- https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor | tee /usr/share/keyrings/cloud.google.gpg > /dev/null
RUN apt-get update && \
    apt-get -y install google-cloud-cli
RUN apt-get -y install kubectl google-cloud-cli-gke-gcloud-auth-plugin

RUN mkdir -p /var/run/sshd /var/log/supervisor
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

RUN apt-get clean all && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /root
CMD ["/usr/bin/supervisord"]
