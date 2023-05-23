FROM cruizba/ubuntu-dind:latest

ENV TZ=US

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone


# set the github runner version
ARG RUNNER_VERSION="2.303.0"


# update the base packages and add a non-sudo user
RUN apt-get update -y && apt-get upgrade -y && useradd -m docker
RUN apt install -y tzdata

# install python and the packages the your code depends on along with jq so we can parse JSON
# add additional packages as necessary
RUN apt-get install -y --no-install-recommends \
    curl jq build-essential libssl-dev libffi-dev python3 python3-venv python3-dev python3-pip git maven vim

# cd into the user directory, download and unzip the github actions runner
RUN cd /home/docker && mkdir actions-runner && cd actions-runner \
    && curl -O -L https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz \
    && tar xzf ./actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz

# install some additional dependencies
RUN chown -R docker ~docker && /home/docker/actions-runner/bin/installdependencies.sh

# copy over the start.sh script
COPY start.sh start.sh

# make the script executable
RUN chmod +x start.sh

#RUN touch /var/run/docker.sock
#RUN chown root:docker /var/run/docker.sock
#RUN usermod -a -G docker docker

# since the config and run script for actions are not allowed to be run by root,
# set the user to "docker" so all subsequent commands are run as the docker user
USER docker
RUN mkdir -p /home/docker/.m2/repository
RUN mkdir -p /home/docker/.ssh

COPY --chown=docker scm_deployment /home/docker/.ssh
COPY --chown=docker .wgetrc /home/docker
COPY --chown=docker settings.xml /home/docker/.m2
COPY --chown=docker .gitconfig /home/docker
RUN chmod 700 /home/docker/.ssh && chmod 600 /home/docker/.ssh/scm_deployment

# set the entrypoint to the start.sh script
ENTRYPOINT ["./start.sh"]
