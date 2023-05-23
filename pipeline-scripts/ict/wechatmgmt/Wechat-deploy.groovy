import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


NOTIFICATION_LIST = "ppiermarini@incomm.com"

String myjson = ""
artifactDeploymentMain="D\$\\WeChatMgmtAPI"
serviceName="AltPayMgmtAPI" 
targetList=[]

targets = [
	'qa':	['sqiipvrcict01v'],
	'prod':	['spiipvrcict01v'],
	'prod_failover':['spiipvrcict02v']
]
//AUVRCICT02 10.4.132.2 
groupId = "com.incomm.ict"
artifactId = "AltPayMgmtAPI"
extension = "jar"
artifactname =""
localArtifact = ""

pipeline {
	agent {
		label "windows"
	}

	parameters {
		choice(name: "targetEnv", choices: ['qa', 'prod'], description: "Environment to build for")
	}

	options {
		disableConcurrentBuilds()
	}

	tools {
		maven 'maven321'
		jdk 'jdk1.8.0_202'
	}



	stages {


		stage('Get Artifact') {
			//when {
			//	expression { params.ENABLE_DEPLOY == true }
			//}
			

			steps {
				cleanWs()

				script {
					artifactname = "${artifactId}-${ARTIFACT}.${extension}"
					echo "Fetching ${artifactname}"
					bat "E:\\\\jenkins-tools\\wget\\wget.exe --no-check-certificate https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/ict/AltPayMgmtAPI/${ARTIFACT}/AltPayMgmtAPI-${ARTIFACT}.jar"
					localArtifact = "AltPayMgmtAPI-${ARTIFACT}.jar"
				}

			}
		}//Get Artifact stage

		stage('Lower Env Demployment') {
			//when {
			//	expression { (params.ENABLE_PROD_DEPLOY == false) }
			//}

			steps {
				echo 'Deploying to Lower environment...'
				script {
					echo "deploying code....."
					deployComponents(targetEnv, targets[targetEnv], "AltPayMgmtAPI-${ARTIFACT}.jar")
				}

			}
		}//deployment stage
		
		

		stage('Finalize') {
			steps {
				echo """
********************************************************************************
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
targets:				${targetList}
--------------------------------------------------------------------------------
"""

		script {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that a new artifact has been uploaded"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """
${ARTIFACT} was deployed to .

**************************************************
Build-Node: ${NODE_NAME}
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
targets:				${targetList}
**************************************************\n\n\n
"""
					}
				}
			}
		}
	}
}

def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentMain, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentMain, Artifact, envName) {
	echo " the target is: ${target_hostname}"
targetList << target_hostname

		bat """
			powershell Get-Host
			powershell Get-Service -Name AltPayMgmtAPI -ComputerName ${target_hostname}  ^| Stop-Service
			ping 127.0.0.1
			del  \\\\${target_hostname}\\${artifactDeploymentMain}\\${artifactId}*.jar
			copy ${localArtifact} \\\\${target_hostname}\\${artifactDeploymentMain}\\
			powershell Get-Service -Name AltPayMgmtAPI -ComputerName ${target_hostname} ^| Start-Service
			echo "sleeping"
			ping 127.0.0.1
		"""
}
//the end