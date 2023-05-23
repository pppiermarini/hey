import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

credsName = "scm_deployment"

gitRepository ="https://github.com/InComm-Software-Development/vms-spil-svc-props.git"
gitBranch="main"
gitCreds = "scm-incomm"

userInput='Promote'
targetEnv="${targetEnv}"
//targetEnv="${target_env}"

testName="myTest"

targets = [
    'dev':  ['10.42.16.191'],
    'qa-a':   ['10.42.83.20'],
	'qa-b':   ['10.42.83.19'],
	'uat':   ['10.42.49.215'],
	'stg':  ['10.41.5.96', '10.41.5.97','10.41.5.98', '10.41.5.99'],
	'backup-prod':  ['10.41.5.231','10.41.5.232','10.41.5.233','10.41.5.234','10.41.5.235','10.41.5.236','10.41.5.237','10.41.5.238','10.41.5.239','10.41.5.240'],
	'DenverPOOL':  ['10.191.5.11','10.191.5.12','10.191.5.17','10.191.5.18','10.191.5.19','10.191.5.20','10.191.5.21','10.191.5.227','10.191.5.228','10.191.5.245','10.191.5.246'],
	'PROD-POOL1': ['10.41.5.17'],
	'PROD-POOL2': ['10.41.5.18'],
	'PROD-POOL3': ['10.41.5.19'],
	'EPAM-DEV':   ['10.42.20.49'],
	'EPAM-QA':   ['10.42.83.142']
]

emailDistribution="jrivett@incomm.com"
//General pipeline 

artifactDeploymentLoc ="/srv/jboss-eap-7.3/standalone_spil/deployments"
propDeploymentLoc="/home/InComm/vms-spil-svc/properties"
srcproperties="/app/jenkins/workspace/vms-spil-svc/vms-spil-svc-deploy"
serviceName="jboss-as-standalone_spil"
pipeline_id="${env.BUILD_TAG}"
tmpLocation="/srv/jboss-eap-7.3/standalone_spil/tmp"
dataLocation="/srv/jboss-eap-7.3/standalone_spil/data"
pidLocation="/var/run/jboss"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.vms'
artifactId = 'vms-spil-svc'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'vms-spil-svc.war'
ArtifactVersion=""
list=""

def approver = ''
def approval_status = '' 
def operators = []
approvalData = [
	'operators': "[ppiermarini,ppattabiraman,vhari,rgadipalli]",
	'adUserOrGroup' : 'ppiermarini,ppattabiraman,vhari,rgadipalli',
	'target_env' : "${targetEnv}"
]

//globals
relVersion="null"



node('linux1'){
	try { 
        cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
			
		stage('Approval'){
		   //if ((targetEnv=='backup-prod')||(targetEnv=='DenverPool')||(targetEnv=='PROD-POOL1')||(targetEnv=='PROD-POOL2')||(targetEnv=='PROD-POOL3'))
			//{
			//	getApproval(approvalData)        	
			//}
		}

		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote'){
				
				//getFromArtifactory()
				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
		list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
		
		artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
		parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
		sleep(3)
		artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
		echo "the artifact version ${artifactVersion}"
		sh "ls -ltr"
		//sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"

			} else {
				echo "not getting artifact during a release build"
			}
			
		}	

		stage("Deployment to ${targetEnv}"){
			if (userInput == 'Promote'){
				
				//serviceStop(targetEnv, targets[targetEnv])
				deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
					if (ENABLE_PROPERTY_FILES == "true"){
					deployPropertyFiles(targetEnv, targets[targetEnv], propDeploymentLoc)
					}
				//serviceStart(targetEnv, targets[targetEnv])
			} else {
				echo "Promote not selected"
			}
		}
		

		
	} catch(exc) {
			currentBuild.result = 'FAILURE'
			sendEmailv3(emailDistribution)
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		currentBuild.result = 'SUCCESS'
		sendEmailv3(emailDistribution, getBuildUserv1()) 
	}
}



///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	}
	parallel stepsInParallel
	
}

def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {

		sh """
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /srv/jboss*'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /home/InComm/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${tmpLocation}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${dataLocation}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/vms-spil-svc*'
			scp -q ${artifactId}-${artifactVersion}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown jboss:jboss ${artifactDeploymentLoc}/${artifactId}-${artifactVersion}.${artExtension}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 750 ${artifactDeploymentLoc}/${artifactId}-${artifactVersion}.${artExtension}'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss '/home/InComm/vms-spil-svc/start-spil-svc.sh' > /dev/null'
		"""
	}

} //end deploy


def serviceStop(envName, targets){
	
	echo "Stop service on ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { stop(it, serviceName, dataLocation, tmpLocation) } ]
	}
	parallel stepsInParallel
	
}

def stop(target_hostname, serviceName, dataLocation, tmpLocation) {

	echo "Stopping service on : ${target_hostname}"

	sshagent([credsName]) {
		sh """
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su jboss -c /home/InComm/vms-spil-svc/stop-spil-svc.sh'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /home/InComm/vms-spil-svc/logs*'
			sleep 10
			"""
	}
}


def serviceStart(envName, targets){
	
	echo "Start service on ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { start(it, serviceName, dataLocation, tmpLocation) } ]
	}
	parallel stepsInParallel
	
}
def start(target_hostname, serviceName, dataLocation, tmpLocation) {
	
	echo "Starting service on : ${target_hostname}"

	sshagent([credsName]) {
		sh """
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su jboss -c /home/InComm/vms-spil-svc/start-spil-svc.sh'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /home/InComm/vms-spil-svc/logs*'
			sleep 10
			"""
	}
}


def getFromArtifactory(){
	// prompts user during stage
	getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"

}

def deployPropertyFiles(envName, targets, propDeploymentLoc){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployProps(it, propDeploymentLoc) } ]
	}
	parallel stepsInParallel
	
}


def deployProps(target_hostname, propDeploymentLoc) {
	echo " the target is: ${target_hostname}"


	//scp -r -q -o StrictHostKeyChecking=no ${srcproperties}/${targetEnvUpper}/* root@${target_hostname}:${propDeploymentLoc}
	targetEnvUpper = targetEnv.toString().toUpperCase()

	sshagent([credsName]) {
		sh """
			scp -r -q -o StrictHostKeyChecking=no ${srcproperties}/${targetEnvUpper}/* root@${target_hostname}:${propDeploymentLoc}/
		"""
	}
}


def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End