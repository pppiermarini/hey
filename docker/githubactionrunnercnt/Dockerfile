FROM ubuntu:18.04

# To make it easier for build and release pipelines to run apt-get,
# configure apt to not require confirmation (assume the -y argument by default)
ENV DEBIAN_FRONTEND=noninteractive
RUN echo "APT::Get::Assume-Yes \"true\";" > /etc/apt/apt.conf.d/90assumeyes

ENV TZ=US

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# set the github runner version
ARG RUNNER_VERSION="2.303.0"


# update the base packages and add a non-sudo user

RUN apt-get update -y && apt-get upgrade -y && useradd -m jenkins
RUN apt install -y tzdata

#install needed packages
RUN apt-get upgrade && apt-get update && apt-get install -y --no-install-recommends \
    sudo \
    ca-certificates \
    curl \
    wget \
    build-essential \
    jq \
    git \
    maven \
    vim \
    iputils-ping \
    libcurl4 \
    libicu60 \
    libunwind8 \
    netcat \
    libssl1.0 \
    openjdk-8-jdk \
  && rm -rf /var/lib/apt/lists/*

RUN curl -LsS https://aka.ms/InstallAzureCLIDeb | bash \
  && rm -rf /var/lib/apt/lists/*

# Can be 'linux-x64', 'linux-arm64', 'linux-arm', 'rhel.6-x64'.
ENV TARGETARCH=linux-x64


#cd into the user directoryand create actions runner dir,download the runner

RUN cd /home/jenkins && mkdir actions-runner && cd actions-runner \
    && curl -O -L https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz \
    && tar xzf ./actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz

RUN chown -R jenkins ~jenkins && /home/jenkins/actions-runner/bin/installdependencies.sh

#set permissions in order to be able to use update-alternatives command in scripts to select jdk
RUN chgrp jenkins /usr/bin/java
RUN chgrp jenkins /etc/alternatives/
RUN chgrp jenkins /var/lib/dpkg/alternatives/
RUN chmod 775 /etc/alternatives/
RUN chmod 775 /var/lib/dpkg/alternatives/

#make the maven repo and ssh key folders
RUN mkdir -p /home/jenkins/.m2/repository
RUN mkdir -p /home/jenkins/.ssh
RUN chown -R jenkins:jenkins /home/jenkins/.m2
RUN chown -R jenkins:jenkins /home/jenkins/.ssh

# copy over needed settings etc,,,
COPY --chown=jenkins:jenkins scm_deployment /home/jenkins/.ssh
COPY --chown=jenkins:jenkins .wgetrc /home/jenkins
COPY --chown=jenkins:jenkins settings.xml /home/jenkins/.m2
COPY --chown=jenkins:jenkins .gitconfig /home/jenkins
RUN chmod 700 /home/jenkins/.ssh && chmod 600 /home/jenkins/.ssh/scm_deployment

ADD ./start.sh /home/jenkins/actions-runner/start.sh

RUN chmod +x /home/jenkins/actions-runner/start.sh

USER jenkins

ENTRYPOINT ["/home/jenkins/actions-runner/start.sh"]