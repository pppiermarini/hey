import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _

credsName = 'scm_deployment'
userInput="Promote"
targetEnv="${target_env}"
testName="myTest"

gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-web-microservices-incomm-digital-auth-provider-service.git"
//gitBranch="${Branch}"
gitCreds="scm-incomm"

targets = [
    'DEV1':  ['10.42.17.81','10.42.18.191','10.42.18.192'],
    'DEV2': ['10.42.18.193','10.42.18.194'],
    'QA1': ['10.42.81.151','10.42.82.64','10.42.82.65'],
	'QA2':   ['10.42.82.66','10.42.82.67'],
	'UAT1':   ['suddcwebapp477v.unx.incommtech.net','sulddrweb01v.unx.incommtech.net'],
	'UAT2':   ['sulddrweb02v.unx.incommtech.net'],
	'Prod': ['spddr01v.unx.incommtech.net', 'spddr02v.unx.incommtech.net', 'spddr03v.unx.incommtech.net'],
	'PreProd': ['spddr99v.unx.incommtech.net']
]

emailDistribution="DigitalDeliveryDevs@incomm.com"

artifactDeploymentLoc ="/var/opt/incomm-digital-auth-provider"
propertyDeploymentPath="" 

serviceName="incomm-digital-auth-provider"

pipeline_id="${env.BUILD_TAG}"


configFolderLoc="/var/opt/incomm-digital-auth-provider/config"

archiveFolderLoc="/var/opt/archives"
//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"


///Artifact Resolver	input specifics

repoId = "maven-release"
groupId="com.incomm.dds.api"
artifactId="incomm-digital-auth-provider"
//artifactVersion="VERSION"
artExtension="jar"
artifactName=""

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))


//globals
chmod="750"
user="svc_springboot"
group="svc_springboot"

userApprove="Welcome to the new Jenkinsfile"
envInput="null"
relVersion="null"
sonarStatus="null"
serviceStatus="null"

currentBuild.result="SUCCESS"

node('linux'){
	try { 
			
		cleanWs()
		//select the artifact

		stage('checkout'){
			cleanWs()
			listGithubBranches(gitCreds,gitRepository)
			echo "Checking out p${env.BRANCH_SCOPE}p"
			sh "git branch"
			cleanWs()
		} 
		stage('Get Artifact'){
			echo "BEGIN"
			echo "artifact ${artifactVersion}"
			echo "artID  ${artifactId}"
			
			if (userInput == 'Promote'){
				githubCheckout(gitCreds,gitRepository,"${env.BRANCH_SCOPE}")
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
			
				sh "ls -ltr"
				sh "touch version.txt && echo ${artifactVersion} > version.txt"
				sh "ls -ltr"

				echo "${artifactId}-${artifactVersion}.${artExtension}"
				
				artifactName="${artifactId}-${artifactVersion}.${artExtension}"
			
			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}")
			//md5SumCheck(targets[targetEnv],artifactDeploymentLoc,"${artifactId}.${artExtension}","${artifactId}.${artExtension}")

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
		 
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def prelimEnv(targetEnv, relVersion){
	
	// prelim staging if needed
	
	echo "${targetEnv}"
	echo "Selected=  ${artifactId}-${relVersion}.${artExtension}"
	echo "DEPLOYING TO ${targetEnv}"
	echo "relVersion= ${relVersion}"
	writeFile file: "relVersion.txt", text: relVersion

	echo "DEPLOYING TO ${targetEnv}"

	localArtifact="${artifactId}-${relVersion}.${artExtension}"
	remoteArtifact="${artifactId}-${relVersion}.${artExtension}"
	
	echo " local artifact=  $localArtifact"
	echo "remote artifact=  $remoteArtifact"	
}


def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	
	echo " the target is: ${target_hostname}"
	//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown svc_springboot:svc_springboot ${artifactDeploymentLoc}/${artifactId}.${artExtension}'

	sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop > /dev/null'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv ${artifactDeploymentLoc}/${artifactId}*.${artExtension}'
			scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:${artifactDeploymentLoc}/${artifactId}.${artExtension}
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${artifactDeploymentLoc}/config_${artifactVersion}_${nowFormatted}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -r ${configFolderLoc}/ ${artifactDeploymentLoc}/config_${artifactVersion}_${nowFormatted}/'
        	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mv ${artifactDeploymentLoc}/config_* ${archiveFolderLoc}/'
        	scp -q -o StrictHostKeyChecking=no -r config/${envName}/. root@${target_hostname}:${configFolderLoc}
        	scp -q -o StrictHostKeyChecking=no version.txt root@${target_hostname}:${artifactDeploymentLoc}/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
		"""
	}
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
	}
	parallel stepsInParallel

}


//def md5(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){
	
//	def validate2 = md5(targets, artifactDeploymentLoc, remoteArtifact, localArtifact)
//	echo "validate2=  $validate2"
//		if("${validate2}" != "0"){
//		echo "${localArtifact} files are different 1"
//		currentBuild.result = 'ABORTED'
//		error('Files do not match...')
//		}else{
//		echo "${localArtifact} files are the same 0"
//		}
//}


def getFromArtifactory(){
//	if (userInput == "Promote"){
	echo "Select an artifact from Artifacory"
	
	relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"
//	} else {
//	echo "not promoting-Skipping"
//	}	
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
        subject: "Jenkins: Deploy ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End