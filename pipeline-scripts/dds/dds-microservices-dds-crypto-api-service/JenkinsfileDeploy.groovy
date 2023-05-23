import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


targetEnv="${target_env}"
testName="myTest"

targets = [
'DEV1':  ['10.42.17.172','10.42.17.173'],
'DEV1-DELTA': ['10.42.17.174'],
'DEV1-TARGET':['10.42.17.175'],
'DEV2': ['10.42.19.197','10.42.19.198'],
'DEV2-DELTA': ['10.42.19.199'],
'DEV2-TARGET': ['10.42.19.200'],
'QA1': ['10.42.80.212','10.42.80.213'],
'QA1-DELTA': ['10.42.80.214'],
'QA1-TARGET': ['10.42.81.147'],
'QA2':   ['10.42.83.111','10.42.83.112'],
'QA2-DELTA':   ['10.42.83.113'],
'QA2-TARGET':   ['10.42.83.114'],
'UAT1':   ['SUDDSAPP488V','SUDDSAPP489V'],
'UAT2':   ['SU02DDS01V','su02dds02v'],
'Prod-Realtime': ['SPDDS01V', 'SPDDS02V', 'SPDDS03V'],
'Prod-Batch': ['SPDDS04V', 'SPDDS05V', 'SPDDS06V'],
'PreProd': ['SPDDS99V']
]


gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-dds-microservices-dds-crypto-api-service.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


emailDistribution="vhari@incomm.com rkale@incomm.com DigitalDeliveryDevs@incomm.com"
//General pipeline 

artifactDeploymentLoc="D\$\\incomm\\microservices\\dds-crypto-api"
serviceName="dds-crypto-api"
archiveLoc="D\$\\incomm\\archives"

pipeline_id="${env.BUILD_TAG}"

configDeploymentLoc="D\$\\incomm\\microservices\\dds-crypto-api\\config"
//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"



///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.dds.microservices.security'
artifactId = 'dds-cypto-api'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'dds-cypto-api.jar'
artifactBareName = 'dds-cypto-api'


//globals
relVersion="null"

currentBuild.result="SUCCESS"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('windows'){
	try { 
			
		cleanWs()
		//select the artifact 
		stage('Get Artifact'){
			githubCheckout(gitCreds,gitRepository,gitBranch)
			artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
			bat"""
			dir
			echo ${artifactVersion} > version.txt
			dir
			"""
		}

		stage('Deployment'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}")
			md5SumCheck(targets[targetEnv],artifactDeploymentLoc,"${artifactId}.${artExtension}","${artifactId}.${artExtension}")

		}
		
		stage('Testing'){

			smokeTesting(targetEnv, targets[targetEnv], testName)

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
	//def stepsInParallel =  targets.collectEntries {
	//	[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	//}
	//parallel stepsInParallel
	
	targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact, envName)
	}
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	
	echo " the target is: ${target_hostname}"
	bat """
			powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Stop-Service
			dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\$artifactId*.${artExtension}
			sleep(4)
			echo "deploying ${artifactName}"
			copy /Y ${artifactName} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			sleep(4)
			echo "Backing up files"
			robocopy /MIR \\\\${target_hostname}\\${configDeploymentLoc}\\ \\\\${target_hostname}\\${archiveLoc}\\config_${artifactBareName}_${artifactVersion}_${nowFormatted}
			sleep(4)
			echo "copying all configs"
			copy /Y config\\${envName}\\*.* \\\\${target_hostname}\\${configDeploymentLoc}\\
			sleep(4)
			echo "Copying the version"
			dir \\\\${target_hostname}\\${configDeploymentLoc}\\
			copy /Y version.txt \\\\${target_hostname}\\${configDeploymentLoc}\\
			dir \\\\${target_hostname}\\${configDeploymentLoc}\\
			sleep(4)
            powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
	"""
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

		targets.each {
		println "Item: $it"
		md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact)
		}
}

def md5(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){
	
	def validate2 = md5w(targets, artifactDeploymentLoc, remoteArtifact, localArtifact)
	echo "validate2=  $validate2"
		if("${validate2}" != "0"){
		echo "${localArtifact} files are different 1"
		currentBuild.result = 'ABORTED'
		error('Files do not match...')
		}else{
		echo "${localArtifact} files are the same 0"
		}
}


def getFromArtifactory(){

	echo "Select an artifact from Artifacory"
	
	relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"

}

def smokeTesting(envName, targets, testName){
	
	//def stepsInParallel =  targets.collectEntries {
	//	[ "$it" : { tests(it, envName, testName) } ]
	//}
	//parallel stepsInParallel
	
	targets.each {
		println "Item: $it"
		tests(it, envName, testName)
	}

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
