import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


userInput="${Build_Type}"
//targetEnv="${targetEnv}"
targetEnv="${target_env}"

testName="myTest"

targets = [
    'dev':  ['10.42.16.191'],
    'qa-a':   ['10.42.82.110'],
	'qa-b':   ['10.42.81.17'],
	'uat':   ['10.42.49.215'],
	'stg':  ['10.41.5.96', '10.41.5.97','10.41.5.98', '10.41.5.99'],
	'backup-prod':  ['10.41.5.231','10.41.5.232','10.41.5.233','10.41.5.234','10.41.5.235','10.41.5.236','10.41.5.237','10.41.5.238','10.41.5.239','10.41.5.240'],
	'DenverPOOL':  ['10.191.5.11','10.191.5.12','10.191.5.17','10.191.5.18','10.191.5.19','10.191.5.20','10.191.5.21','10.191.5.227','10.191.5.228','10.191.5.245','10.191.5.246'],
	'PROD-POOL1': ['10.41.5.17'],
	'PROD-POOL2': ['10.41.5.18'],
	'PROD-POOL3': ['10.41.5.19'],
	'EPAM-DEV':   ['10.42.20.49'],
	'EPAM-QA':   ['10.42.83.142'],
]

emailDistribution="ppiermarini@incomm.com"
//General pipeline 

artifactDeploymentLoc ="/srv/jboss-eap-7.3/standalone_csr/deployments"
serviceName="jboss-as-standalone_csd"
pipeline_id="${env.BUILD_TAG}"
tmpLocation="/srv/jboss-eap-7.3/standalone_csr/tmp"
dataLocation="/srv/jboss-eap-7.3/standalone_csr/data"
pidLocation="/var/run/jboss"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.vms.csr'
artifactId = 'csr'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'inComm_CSD_JBoss.war'

def approver = ''
def approval_status = '' 
def operators = []
approvalData = [
	'operators': "[ppiermarini,ppattabiraman,vhari,rgadipalli,nswamy,sminuku,vstanam]",
	'adUserOrGroup' : 'ppiermarini,ppattabiraman,vhari,rgadipalli,nswamy,sminuku,vstanam',
	'target_env' : "${targetEnv}"
]

//globals
relVersion="null"



node('linux'){
	try { 
        cleanWs()
			
		stage('Approval'){
		   //if ((targetEnv=='backup-prod')||(targetEnv=='DenverPool')||(targetEnv=='PROD-POOL1')||(targetEnv=='PROD-POOL2')||(targetEnv=='PROD-POOL3'))
			//{
			//	getApproval(approvalData)        	
			//}
		}

		//select the artifact 
		stage('Get Artifact'){

			
			if (userInput == 'Promote'){
				
			//Select and download artifact
            list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
            echo "the list contents ${list}"
            artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
            parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
            sleep(3)
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)

			} else {
				echo "not getting artifact during a release build"
			}
			
		}  //${artifactId}-${artifactVersion}.${artExtension}
		
		stage("ServiceOps"){
			echo "Performing Service operation"

			if (userInput == 'STOP_Service'){
				serviceStop(targetEnv, targets[targetEnv])
			} else if (userInput == 'START_Service'){
				serviceStart(targetEnv, targets[targetEnv])
			} else {
				echo "Service reboot handled during deployment"
			}
		}

		stage("Deployment to ${targetEnv}"){

			if (userInput == 'Promote'){
				deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			} else {
				echo "not deploying during a release build"
			}

		}
		
		stage('Testing'){

			if ((userInput == 'Build')||(userInput == 'Promote')){
			smokeTesting(targetEnv, targets[targetEnv], testName)
			} else {
				echo "not testing during a release build"
			}

		}

		
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		notifyBuild(emailDistribution)
		//sendEmail(emailDistribution, userInput, gitBranch) 
		}
	}

} //end of node


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


	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh /home/InComm/CSR/jboss_shutdown_csr.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /srv/jboss*'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /home/InComm/CSR'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${tmpLocation}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${dataLocation}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/inComm_CSD_JBoss*'
		scp -i ~/.ssh/pipeline ${artifactId}-${artifactVersion}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}/
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown jboss:jboss ${artifactDeploymentLoc}/${artifactId}-${artifactVersion}.${artExtension}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 750 ${artifactDeploymentLoc}/${artifactId}-${artifactVersion}.${artExtension}'
	"""
	if (targetEnv!='backup-prod'){
		if ((targetEnv=='stg')||(targetEnv=='uat')||(targetEnv=='qa-a')||(targetEnv=='qa-b')||(targetEnv=='dev')){
        sh """
		  ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su jboss -c '/home/InComm/CSR/jboss_startup_csr.sh' > /dev/null'
		  sleep 30
		  ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find ${artifactDeploymentLoc} -type f -name ${artifactName}*.failed | wc -l' > commandresult
		"""
		} else if ((targetEnv=='PROD-POOL1')||(targetEnv=='PROD-POOL2')||(targetEnv=='PROD-POOL3')) {
        sh """
		  ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su jboss -c '/home/InComm/CSR/jboss_startup_csr_7.3.sh' > /dev/null'
		  sleep 30
		  ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find ${artifactDeploymentLoc} -type f -name ${artifactName}*.failed | wc -l' > commandresult
		"""			
			
		}
		def r = readFile('commandresult').trim()
			echo "arr= p${r}p"
			if(r == "1"){
			echo "failed deployment"
			currentBuild.result = 'FAILED'
			} else {
			echo "checking for deployed"
			
			try {		
			timeout(1) {
				waitUntil {
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find ${artifactDeploymentLoc} -type f -name ${artifactId}*.deployed | wc -l' > commandresult"""
						def a = readFile('commandresult').trim()
						echo "arr= p${a}p"
						if (a == "0"){
						return true;
						}else{
						return false;
						}
				   }
				   
				}
		} catch(exception){
			echo "${artifactName} did NOT deploy properly. Please investigate"
			abortMessage="${artifactName} did NOT deploy properly. Please investigate"
			currentBuild.result = 'FAILED'

		} //if not backup-prod
				}
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
	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh /home/InComm/CSR/jboss_shutdown_csr.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /logs*'
		sleep 20
		"""
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
	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su jboss -c '/home/InComm/CSR/jboss_startup_csr.sh' > /dev/null'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /logs*'
		sleep 20
		"""
}

def getFromArtifactory(){
	// prompts user during stage
	getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"

}

def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def tests(target, envName, testName){
	
	echo " Smoke Testing on ${target}"
	echo "my test = ${testName}"
	sleep(1)
		dir('testresults'){
			//println "Run Test Script"
			//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
			// String results = readFile 'testresults.xml'
		}
		//if(1 ){
		//	println "ERROR todo "
		//} else {
		//	println "results"
		//}
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
