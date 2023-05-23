import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


emailDistribution="rkale@incomm.com vhari@incomm.com vpilli@incomm.com mpalve@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

maven="/opt/apache-maven-3.9.9/bin/mvn"

targetenvs="${targetEnv}"

//imageLabel = "${Image_label}"
//currentBuild.result="SUCCESS"

imageName = "spl-appconfig-client-service"

imageDeploymentLoc="/app/docker/tmp"

configLoc="/app/install/appconfigclient"

gitYmlInstallFile = "acf-web-client-docker-compose.yml"

commandFile="command.sh"
composeymlLoc = ""
gitLoc = "src/main/resources/docker"
Deploy_Config="${Config}"

gitRepository="https://github.com/InComm-Software-Development/ctps-spil-shared-services-appconfigclient.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


//@TODO work with team to get a key value for CAT1: 
//'10.41.4.218','10.41.4.219','10.41.4.247','10.41.4.248','10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207'
//'pre-prod':['10.40.7.214','10.40.7.215','10.40.7.229','10.40.7.230'],
//    'prod':['10.40.7.231','10.40.7.232','10.40.7.233','10.40.7.210','10.40.7.211','10.40.7.212','10.40.7.213'],
targets = [
    'dev':  ['10.42.20.20','10.42.16.106','10.42.16.107','10.42.20.21'],
    'qa': ['10.42.82.92','10.42.82.93','10.42.82.239','10.42.82.240'],
    'uat':['10.42.50.93','10.42.50.94','10.42.50.200','10.42.50.201'],
    'load': ['10.42.5.203','10.42.5.204','10.42.5.205','10.42.5.206','10.42.5.207','10.42.5.208','10.42.5.209'],
    'pre-prod':['10.40.7.214','10.40.7.215','10.40.7.229','10.40.7.230'],
    'prod':['10.40.7.231','10.40.7.232','10.40.7.233','10.40.7.210','10.40.7.211','10.40.7.212','10.40.7.213'],
    'cat1-pre-prod': ['10.41.4.218','10.41.4.219','10.41.4.247','10.41.4.248'],
    'cat1-prod': ['10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207'],
    'devsrl': ['10.42.16.16','10.42.16.18','10.42.16.21','10.42.16.125'],
    'qasrl': ['10.42.82.250','10.42.82.251','10.42.82.252','10.42.82.253'],
    'uatsrl': ['10.42.49.100','10.42.49.101','10.42.49.182','10.42.49.184'],
    'mil-pre-prod': ['10.40.98.222','10.40.98.223','10.40.98.228','10.40.98.229'],
    'mil-prod': ['10.40.98.230','10.40.98.231','10.40.98.232','10.40.98.233']


]

approvalData = [
	'operators': "[ppiermarini,vpilli,vhari,mpalve,psubramanian]",
	'adUserOrGroup' : 'ppiermarini,vpilli,vhari,mpalve,psubramanian',
	'target_env' : "${targetenvs}"
]

def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''
node('linux1'){
	try { 
		
		cleanWs()
		stage('Approval') {
                if (targetenvs == "uat" || targetenvs == "pre-prod" || targetenvs == "prod" || targetenvs == "pre-prod-cat1" || targetenvs == "prod-cat1") {
                    getApproval(approvalData)       
                } else {
                    echo "Deploying to ${targetenvs} doesn't required any approvals"
                }
        }


		stage("Deploy to ${targetenvs}") {
			githubCheckout(gitCreds,gitRepository,gitBranch)

			echo "{$targetenvs}"
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
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slac
	}
}

} //end of node


def deployComponents(envName, targets, Deploy_Config){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName, Deploy_Config) } ]
	}
	parallel stepsInParallel


	
}

def deploy(target_hostname, envName, Deploy_Config) {
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {	

		echo "Generating the directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
		"""
	
		echo "deployconfig set to : " + Deploy_Config
		if (Deploy_Config == 'Y'){
			sh """
				scp -q -o StrictHostKeyChecking=no ${gitLoc}/${gitYmlInstallFile} root@${target_hostname}:${configLoc}/
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 ${configLoc}/${gitYmlInstallFile}'
			"""
		}

	    sh"""scp -q -o StrictHostKeyChecking=no ${gitLoc}/${commandFile} root@${target_hostname}:${configLoc}/
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 ${configLoc}/${commandFile}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh ${configLoc}/${commandFile}'

		"""
	}

}



///// The End
