
mkdir devops_dockeragent

put these file in the folder along with settings.xml

then run 

docker build --network=host -t jdk_mvndockeragent:latest .


docker run -d -e AZP_URL=https://dev.azure.com/ppiermarini -e AZP_TOKEN=jdiktprqtiiumamvhcbkjsjlrzx37vj3jcr774yyhse4jevld6ga -e AZP_POOL=Container_Pool -e AZP_AGENT_NAME=Grumpy --dns=10.83.31.40 jdk_mvndockeragent:latest


the agent should show up in azure devops POOL


