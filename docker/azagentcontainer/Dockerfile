FROM ubuntu:18.04

# To make it easier for build and release pipelines to run apt-get,
# configure apt to not require confirmation (assume the -y argument by default)
ENV DEBIAN_FRONTEND=noninteractive
RUN echo "APT::Get::Assume-Yes \"true\";" > /etc/apt/apt.conf.d/90assumeyes

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    jq \
    git \
    iputils-ping \
    libcurl4 \
    libicu60 \
    libunwind8 \
    netcat \
    libssl1.0 \
    openjdk-8-jdk \
    maven \
    wget \
  && rm -rf /var/lib/apt/lists/*

RUN curl -LsS https://aka.ms/InstallAzureCLIDeb | bash \
  && rm -rf /var/lib/apt/lists/*

# Can be 'linux-x64', 'linux-arm64', 'linux-arm', 'rhel.6-x64'.
ENV TARGETARCH=linux-x64

RUN useradd -ms /bin/bash jenkins

RUN mkdir -p /home/jenkins/.m2/repository
RUN mkdir -p /azp/.ssh

WORKDIR /azp
COPY scm_deployment /azp/.ssh
COPY settings.xml /home/jenkins/.m2
COPY ./start.sh .

RUN chmod 700 /azp/.ssh && chmod 600 /azp/.ssh/scm_deployment
RUN chmod +x start.sh
RUN chown -R jenkins:jenkins /home/jenkins/

ENTRYPOINT ["./start.sh"]
