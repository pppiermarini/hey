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
Deploy_Config="${Config}"

targets = [
'dev':  ['10.42.20.42'],
'dev-ddp':  ['10.42.20.33','10.42.20.34','10.42.17.165']
]

gitRepository="https://github.com/InComm-Software-Development/cfes-atalla-proxy-enhancement.git"
gitBranch="${Git_Configuration}"
gitCreds="scm-incomm"
configCommon="src\\main\\resources"
credsName = "scm_deployment"

emailDistribution="ppiermarini@incomm.com"
//General pipeline 

artifactDeploymentLoc = "C\$\\AtallaConnection\\deploy"
artifactLiveLoc = "C\$\\AtallaConnection"
serviceName = "AtallaConnection"

pipeline_id="${env.BUILD_TAG}"

//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"

Hmmm, not in artifactory....

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = ''
artifactId = ''
env_propertyName = 'ART_VERSION'
artExtension = ''
artifactName = ''


//globals
relVersion="null"

currentBuild.currentResult="SUCCESS"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('windows'){
	try { 
			
		cleanWs()
		//select the artifact 
		stage('Get Artifact'){
			
			echo "deployconfig set to :" + Deploy_Config
			if (Deploy_Config == 'Y'){
				githubCheckout(gitCreds,gitRepository,gitBranch)
			}
			artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
			bat"""
			echo ${artifactVersion} > ${artifactId}_version.txt
			"""
		}

		stage('Deployment'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}", Deploy_Config)
		}
		
	} catch(exc) {
			currentBuild.currentResult="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.currentResult == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{

		echo "The artifact Version deployed is: ${artifactVersion}"
	}
}

} //end of node


def deployComponents(envName, targets, Artifact, Deploy_Config){
	
	echo "my env= ${envName}"
	//def stepsInParallel =  targets.collectEntries {
	//	[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	//}
	//parallel stepsInParallel
	
	targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact,envName, Deploy_Config)
	}
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName, Deploy_Config) {

	/*sleep(4)
			echo "copying all configs"
			copy /Y config\\${envName}\\*.* \\\\${target_hostname}\\${configDeploymentLoc}\\
			sleep(4)*/
	
	echo "the target is: ${target_hostname}"
	bat """	
			powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Stop-Service
			dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			echo "Backing up files"
			robocopy /MIR \\\\${target_hostname}\\${artifactDeploymentLoc}\\ \\\\${target_hostname}\\${archiveLoc}\\${artifactId}_${nowFormatted}			
			del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\$artifactId*.${artExtension}
			sleep(4)
			echo "deploying ${artifactName}"
			copy /Y ${artifactName} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			sleep(4)
			echo "deployconfig set to : + Deploy_Config"
		"""
		echo "deployconfig set to :" + Deploy_Config
		if (Deploy_Config == 'Y'){
		bat """
			echo "Backing up files"
			robocopy /MIR \\\\${target_hostname}\\${configDeploymentLoc}\\ \\\\${target_hostname}\\${archiveLoc}\\config_${artifactId}_${artifactVersion}_${nowFormatted}
			echo "Copying the version"
			dir \\\\${target_hostname}\\${configDeploymentLoc}\\
			copy /Y ${artifactId}_version.txt \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			copy /Y ${configCommon}\\ \\\\${target_hostname}\\${configDeploymentLoc}\\
			copy /Y ${configCommon}\\${envName}\\ \\\\${target_hostname}\\${configDeploymentLoc}\\
        """
	}else {
		echo "Property files not copied"
	}
	   bat """
        echo "Starting service"
	powershell Start-Sleep -s 5
	
	    powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
	powershell Start-Sleep -s 5
	    
	   """
}

///// The End
