FROM jenkins/jenkins:lts-jdk17

# maven for the pipeline's builds, installed as root, then back to the jenkins user
USER root
RUN for f in /etc/apt/sources.list.d/debian.sources /etc/apt/sources.list; do \
        [ -f "$f" ] && sed -i -e 's|http://deb.debian.org|https://deb.debian.org|g' -e 's|http://security.debian.org|https://security.debian.org|g' "$f"; \
    done; \
    apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends ca-certificates maven openssh-client unzip \
    && rm -rf /var/lib/apt/lists/*

# docker client only, it talks to the host's daemon through the mounted socket
COPY --from=docker:27-cli /usr/local/bin/docker /usr/local/bin/docker

# aws cli v2
RUN curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/awscliv2.zip /tmp/aws

# the container runs as root but the workspaces belong to jenkins, so git would refuse them as dubious ownership
RUN git config --system --add safe.directory '*'
USER jenkins
