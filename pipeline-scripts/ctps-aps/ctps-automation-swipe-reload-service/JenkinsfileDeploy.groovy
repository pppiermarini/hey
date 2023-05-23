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
'dev':['10.42.17.56']
]

//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/ctps-automation-swipe-reload-service.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

//
emailDistribution="dstovall@incomm.com"
//General pipeline 

artifactDeploymentLoc="D\$\\incomm\\jboss\\deploy\\shared\\"
serviceName="JBOSS-SWIPE"
pipeline_id="${env.BUILD_TAG}"

//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"


///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.swipe-reload'
artifactId = 'swipereload-service'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'swipereload-service.war'
artifactBareName = 'swipereload-webservice'


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
            powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
	"""
}

///// The End
