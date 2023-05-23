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

//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-dds-microservices-dds-admin-api.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

//
emailDistribution="DigitalDeliveryDevs@incomm.com"
//General pipeline 

artifactDeploymentLoc="D\$\\incomm\\microservices\\dds-admin-api"
serviceName="dds-admin-api"
archiveLoc="D\$\\incomm\\archives"
pipeline_id="${env.BUILD_TAG}"

configDeploymentLoc="D\$\\incomm\\microservices\\dds-admin-api\\config"
//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"



///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.web.dds'
artifactId = 'dds-admin-api'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'dds-admin-api.jar'
artifactBareName = 'dds-admin-api'


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
		}
		
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{

		echo "The artifact Version deployed is: ${artifactVersion}"
	}
}

} //end of node


def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	//def stepsInParallel =  targets.collectEntries {
	//	[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	//}
	//parallel stepsInParallel
	
	targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact,envName)
	}
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {

	//
	
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

///// The End
