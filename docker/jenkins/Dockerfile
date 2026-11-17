FROM jenkins/jenkins:lts-jdk17

# maven for the pipeline's builds, installed as root, then back to the jenkins user
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
USER jenkins
