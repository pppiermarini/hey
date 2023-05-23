import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

emailDistribution="shaque@incomm.com rkale@incomm.com vhari@incomm.com skeshari@InComm.com mmurarisetty@incomm.com stadoori@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

maven="/opt/apache-maven-3.9.9/bin/mvn"

//Image name at Docker Artifactory.
imageName = "ctps-tokenization-svc"

imageDeploymentLoc="/app/docker/tmp"

Deploy_Config="${Config}"

ymlConfigLoc="/app/install/ctps-tokenization-svc/config"
configLoc="/app/install/ctps-tokenization-svc/scripts"
gitLoc="src/main/resources/"

gitAppYmlFile="application.yml"
gitYmlInstallFile="token-svc-docker-swarm.yml"

commandFile="command.sh"

targetenvs="${targetEnv}"

gitRepository="https://github.com/InComm-Software-Development/ctps-tokenization-svc.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

//Targets 
targets = [
    'dev': ['10.42.20.39','10.42.20.40','10.42.19.214'],
    'qa': ['10.42.83.140','10.42.83.141','10.42.81.60'],
    'uat':['10.42.50.218','10.42.50.219','10.42.50.52'],
	'pre-prod':['10.41.7.157','10.41.7.158','10.41.7.159'],
	'prod':['10.41.7.151','10.41.7.152','10.41.7.153']

]

//approval data of list of admins
approvalData = [
	'operators': "[ppiermarini,shaque,vhari,skeshari,wzhan,hpokala,shaque]",
	'adUserOrGroup' : 'ppiermarini,shaque,vhari,skeshari,wzhan,hpokala,shaque',
	'target_env' : "${targetEnv}"
]


def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''

node('linux1'){
	try { 
			
		cleanWs()
		stage('Approval Check') {
        	if ((targetEnv == 'pre-prod') || (targetEnv == 'prod')) {
        		getApproval(approvalData)
        	}
        }

		stage("Deploy to ${targetenvs}") {
			githubCheckout(gitCreds,gitRepository,gitBranch)

			deployComponents(targetenvs, targets[targetenvs], Deploy_Config)

		}
	
		
	} catch(exc) {
			//currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	echo "${currentBuild.result}"
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	//<li>ServerNames: ${targetEnv}= ${Servers}</li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.result}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if (currentBuild.result == "ABORTED"){
		echo "if aborted"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.result}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.result == "SUCCESS"){
		echo "success"
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.result}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""				
	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node


def deployComponents(envName, targets, Deploy_Config){
	
	echo "my env= ${envName}"
	echo "my target= ${targets}"
	echo "Deploy config: " + Deploy_Config
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName, Deploy_Config) } ]
	}

	parallel stepsInParallel

	
}

def deploy(target_hostname, envName, Deploy_Config) {
	echo " the target is: ${target_hostname}"
	//				scp -q -o StrictHostKeyChecking=no ${gitLoc}/${gitAppYmlFile} root@${target_hostname}:${ymlConfigLoc}
	sshagent([credsName]) {
		echo "Deploying the image"
		echo "Generating the directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
		"""
		echo "deployconfig set to : " + Deploy_Config
		if (Deploy_Config == 'Y'){
			sh """
				scp -q -o StrictHostKeyChecking=no ${gitLoc}/docker/${envName}/${gitYmlInstallFile} root@${target_hostname}:${configLoc}

			"""
		}
	    sh"""
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/docker/${envName}/${commandFile} root@${target_hostname}:${configLoc}
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh ${configLoc}/${commandFile}'

		"""
		}
}



///// The End
