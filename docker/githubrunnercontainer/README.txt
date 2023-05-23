https://github.com/InComm-Software-Development/v3-pipeline-scripts/tree/development/docker/githubrunnercontainer


mkdir folder  /app/jenkins/dind_ubuntu  in linux workspace (obviously the name could be anything that makes sense)


cp these files from github into your folder and do the chmod and chown stuff

log into the linux server and become jenkins user

Build the container with this command:
>> docker build --network=host --tag gh-runner-dind03 .

Run the container with this command:
>> docker run -d --dns=10.83.31.40 -e ORGANIZATION=InComm-Software-Development -e ACCESS_TOKEN=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 -e RUNNER_NAME=spscmbuild03_ct -e LABELS=build03_container --name dind-runner03 gh-runner-dind03


the container runner will appear in github


go to the github ORG
Click on the settings cog (upper right)

on the left scroll to Actions -> Runners



NOTES
5/10/23  trying to add jdk8
docker build --network=host --tag gh-runner-dind07 .
docker run -d --dns=10.83.31.40 -e ORGANIZATION=InComm-Software-Development -e ACCESS_TOKEN=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 -e RUNNER_NAME=spscmbuild07_ct -e LABELS=build07_container --name dind-runner07 gh-runner-dind07
