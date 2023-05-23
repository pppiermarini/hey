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
    'DEV':  ['10.42.17.59'],
    'QA': ['10.42.81.198'],
    'UAT': ['10.42.48.193', '10.42.50.189']

]




emailDistribution="ppiermarini@incomm.com vhari@incomm.com rkale@incomm.com khande@incomm.com"
//General pipeline 

//@Confirm from Kaustaubh
artifactDeploymentLoc="D\$\\OpenCard-EntryService\\"
serviceName="OpenCard-EntryService"

pipeline_id="${env.BUILD_TAG}"


//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"

gitCreds="scm-incomm"
///Artifact Resolver input specifics, TBD
repoId = 'maven-release'
groupId = 'com.incomm.opencard'
artifactId = 'OpenCardEntryService'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''
artifactBareName = ''


//globals
relVersion="null"

currentBuild.result="SUCCESS"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

approvalData = [
	'operators': "[ppiermarini,vhari,khande]",
	'adUserOrGroup' : 'ppiermarini,vhari,khande',
	'target_env' : "${targetEnv}"
]


node('windows'){
	try { 
			
		cleanWs()
		//select the artifact 

		stage('Approval Check') {
        	if ((targetEnv == 'QA') || (targetEnv == 'UAT')) {
        		getApproval(approvalData)
        	}
        }

		stage('Get Artifact'){
			
			//githubCheckout(gitCreds,gitRepository,gitBranch)
			artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	

		}

		stage('Deployment'){
			
			deployComponents(targetEnv, targets[targetEnv], "${artifactId}-${artifactVersion}.${artExtension}")
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


def deployComponents(envName, targets, Artifact){

	targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact, envName)
	}
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	
	echo " the target is: ${target_hostname}"
	echo "The artifact to deploy: ${Artifact}"
	bat """
			powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Stop-Service
			dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\$artifactId*.${artExtension}
			powershell Start-Sleep -s 5
			echo "deploying ${Artifact}"
			copy /Y ${Artifact} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			powershell Start-Sleep -s 5
	        powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
	"""
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
