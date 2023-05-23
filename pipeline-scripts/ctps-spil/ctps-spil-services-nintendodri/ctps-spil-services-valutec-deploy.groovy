import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


emailDistribution="vpilli@incomm.com mpalve@incomm.com psubramanian@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

maven="/opt/apache-maven-3.9.9/bin/mvn"

//	imageLabel = "${Image_label}"
//currentBuild.result="SUCCESS"

//@TODO: Waiting on update from Dev - RITM0624122
imageName = "spl-services-valutec"

imageDeploymentLoc="/app/docker/tmp"

Deploy_Config="${Config}"

//@TODO: Waiting on update from Dev - RITM0624122
configLoc="/app/install/valutec/"
gitLoc="docker"

//@TODO: Waiting on update from Dev - RITM0624122
gitYmlInstallFile="spil-valutec-svc-docker-compose.yml"

commandFile="command.sh"

targetenvs="${targetEnv}"

gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-valutec.git"
gitBranch= "${Branch}" //${Branch}
gitCreds="scm-incomm"

//https://github.com/InComm-Software-Development/ctps-dbservices-project-initdbservices.git

//RITM0615744 - Get an update on the manager node from psubramanian for Cat pre-prod and prod - Completed
//@TODO: We need to either had a set of config folders called pre-prod-cat1 and prod-cat1 for Cat 1 envs or write logic to set that based on git repo.

// ['10.40.7.214'] -> pre-prod CAT3, ['10.40.7.231'] -> prod CAT3
// 'pre-prod': ['10.41.4.218'],
//    'prod': ['10.41.6.97'] 
targets = [
    'dev':  ['10.42.20.20'],
    'qa': ['10.42.82.239'],
    'uat': ['10.42.50.200'],
    'pre-prod': ['10.40.7.214'],
    'prod': ['10.40.7.231']
]

approvalData = [
	'operators': "[ppiermarini,vpilli,vhari,mpalve,psubramanian]",
	'adUserOrGroup' : 'ppiermarini,vpilli,vhari,mpalve,psubramanian',
	'target_env' : targetenvs
]


def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''

node('linux1'){
	try { 
			
		cleanWs()

		stage('Approval Check') {
        	if (targetenvs == "uat" || targetenvs == "pre-prod" || targetenvs == "prod" || targetenvs == "pre-prod-cat1" || targetenvs == "prod-cat1") {
        		getApproval(approvalData)
        	}
        }

		stage("Deploy to ${targetenvs}") {
			githubCheckout(gitCreds,gitRepository,gitBranch)

			deployComponents(targetenvs, targets[targetenvs], Deploy_Config)

		}
	
		
	} catch(exc) {
			currentBuild.result="FAILED"
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
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker import ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	//		scp -q -o StrictHostKeyChecking=no ${imageName}-${imageLabel}.tar root@${target_hostname}:${imageDeploymentLoc}/
	//	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	//	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -f ${imageDeploymentLoc}/${imageName}*.tar'

	//@TODO: To be tested logic for the repository. ---> THis is not neccessary to be added check targets array, mapping is completed and confirmed by Prem. S.
	/*
	echo "envName is: ${envName}"
	if (envName == "pre-prod-cat1") {
		envName = "pre-prod"
	}
	else if (envName == "prod-cat1") {
		envName = "prod"
	}*/

	sshagent([credsName]) {
		echo "Deploying the image"
		echo "Generating the directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
		"""
		echo "deployconfig set to : " + Deploy_Config
		if (Deploy_Config == 'Y'){
			sh """
				scp -q -o StrictHostKeyChecking=no ${gitLoc}/${envName}/${gitYmlInstallFile} root@${target_hostname}:${configLoc}
			"""
		}
	    sh"""
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${envName}/${commandFile} root@${target_hostname}:${configLoc}
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh ${configLoc}/${commandFile}'
		"""
		}
}



///// The End
