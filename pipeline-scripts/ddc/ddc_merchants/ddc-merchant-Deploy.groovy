import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


//
//
//     This Script not complete  -pp
//

gitUrl = "https://github.com/InComm-Software-Development/ddc-merchants.git"
gitCreds="scm-incomm"
credsName = "scm_deployment"

NOTIFICATION_LIST = "ppiermarini@incomm.com"

String myjson = ""
artifactDeploymentLoc ="/var/www/ddc-static"
httpConf="/etc/httpd/conf"
httpConfd="/etc/httpd/conf.d"
configFolderLoc="configuration"
tmpFolder="/tmp"
serviceName="httpd"
artifactName="null"
htaccess=".htaccess"

ENABLE_PROPERTIES_DEPLOY="false"

targetList=[]
ENV=""
TMP=""
TAG = "null"
WORKSPACE = "null"

targetEnv="${target_env}"

targets = [
	'dev':	['sdddcwebapp468v.unx.incommtech.net', 'sdddcwebapp469v.unx.incommtech.net'],
]
//sdddcwebapp468v.unx.incommtech.net  10.42.17.80
//sdddcwebapp469v.unx.incommtech.net  10.42.17.81


approvalData = [
	'operators': "[ppiermarini]",
	'adUserOrGroup' : 'ppiermarini',
	'target_env' : "${targetEnv}"
]

repoId = "maven-all"
groupId="com.incomm.web"
artifactId="ddc-merchants"
artExtension="jar"
configEnv=""
displayBranch=""

user=""
group=""
chmod="750"


pipeline {
	agent {
		label "linux1"
	}

	parameters {
		listGitBranches name: 'displayBranch', branchFilter: 'refs/heads/(*.*)', defaultValue: 'develop', selectedValue: 'DEFAULT', type: 'PT_BRANCH_TAG', remoteURL: "${gitUrl}", credentialsId: 'scm-incomm', description: "create artifact from ${gitUrl}"
		//text(name: 'displayBranch', defaultValue: 'master', description: 'Enter the branch name')
		//booleanParam(name: 'ENABLE_PROPERTIES_DEPLOY', defaultValue: false, description: 'Select to deploy property files')
		booleanParam(name: 'ENABLE_PROD_DEPLOY', defaultValue: false, description: 'Select when deploying to production')
		//choice(name: "targetEnv", choices: ['dev'], description: "Environment to deploy to")
		//booleanParam(name: 'ENABLE_UNIT_TESTS', defaultValue: true, description: 'run unit tests')
	}

	options {
		disableConcurrentBuilds()
	}

	tools {
		maven 'maven321'
		jdk 'openjdk-11.0.5.10'
	}


	stages {

		stage('Initialize') {
			steps {
				cleanWs()

					
				script {
					githubCheckout(gitCreds,gitUrl,displayBranch)
				}
			}
		}//initialize

		stage('Approval Check') {
	
			steps{
				script{
					approveResult = getApproval(approvalData)
					echo "Approval Result: ${approveResult}"
				}
			}
		}


/*
		stage('Property file Demployment') {
			when {
				expression { (params.ENABLE_PROPERTIES_DEPLOY == true) }
			}

			steps {
				echo 'Deploying property files...'
				script {
					echo "deploying code....."
					deployPropertyFiles(targetEnv, targets[targetEnv], "dpam-ui-${artifactVersion}.tar")
				}

			}
		}//property file stage
*/

		stage('Lower Env Demployment') {
			when {
				expression { (params.ENABLE_PROD_DEPLOY == false) }
			}

			steps {
				echo 'Deploying to Lower environment...'
				script {
					echo "deploying code....."
					deployComponents(targetEnv, targets[targetEnv])
				}

			}
		}//deployment stage
		
		stage('Production Env Demployment') {
			when {
				expression { (params.ENABLE_PROD_DEPLOY == true)}
			}

			steps {
				echo 'Deploying to Production environment...'
				script {
					echo "deploying code....."
					deployComponents(targetEnv, targets[targetEnv])
				}

			}
		}//deployment stage
		
		
		
		stage('Finalize') {
			steps {
				echo """
				
********************************************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
Deployment-Branch: 		${displayBranch}
Environment:			${targetEnv}
targets:				${targetList}
--------------------------------------------------------------------------------
"""

		script {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that deployment has taken place"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """
${artifactId} version ${artifactVersion} has been deployed from the ${displayBranch} branch.


**************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
Deployment-Branch: 		${displayBranch}
Environment:			${targetEnv}
targets:				${targetList}
**************************************************\n\n\n
"""
					}
				}
			}
		}
	}
}

///////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////
def deployComponents(envName, targets){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc) } ]
	}
	parallel stepsInParallel
	
}

def deployPropertyFiles(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployprops(it, artifactDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc) {
	echo " the target is: ${target_hostname}"
targetList << target_hostname
	sshagent([credsName]) {
		sh """

		scp -r -q -o StrictHostKeyChecking=no css/'*' root@${target_hostname}:${artifactDeploymentLoc}

		"""
	}
}
//httpConf="/etc/httpd/conf"
//httpConfd="/etc/httpd/conf.d"

def deployprops(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl stop ${serviceName}'
			scp -q -o StrictHostKeyChecking=no ${configFolderLoc}/${configEnv}/${htaccess} root@${target_hostname}:${artifactDeploymentLoc}
			scp -q -o StrictHostKeyChecking=no ${configFolderLoc}/${configEnv}/httpd.conf root@${target_hostname}:${httpConf}
			if [ -e ${configFolderLoc}/${configEnv}/ssl.conf ]; then scp -q -o StrictHostKeyChecking=no ${configFolderLoc}/${configEnv}/ssl.conf root@${target_hostname}:${httpConfd}; fi
			if [ -e ${configFolderLoc}/${configEnv}/welcome.conf ]; then scp -q -o StrictHostKeyChecking=no ${configFolderLoc}/${configEnv}/welcome.conf root@${target_hostname}:${httpConfd}; fi
			if [ -e ${configFolderLoc}/${configEnv}/autoindex.conf ]; then scp -q -o StrictHostKeyChecking=no ${configFolderLoc}/${configEnv}/autoindex.conf root@${target_hostname}:${httpConfd}; fi
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'chown -R ${user}:${group} ${artifactDeploymentLoc} && chmod ${chmod} ${artifactDeploymentLoc}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'find ${artifactDeploymentLoc}/ -type d -exec chmod 750 \'{}\' \\;'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'find ${artifactDeploymentLoc}/ -type f -exec chmod 640 \'{}\' \\;'
		"""
	}
}


def getArtifact() {
	list = artifactResolverV2(projectProperties.buildInfo.repoId, projectProperties.buildInfo.groupId, projectProperties.buildInfo.artifactId, projectProperties.deployInfo.artExtension)
            
	echo "the list contents ${list}"

	artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
    parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'artifactVersion')]
    sleep(3)
    artifactWget(projectProperties.buildInfo.repoId, projectProperties.buildInfo.groupId, projectProperties.buildInfo.artifactId, projectProperties.deployInfo.artExtension, artifactVersion)
    echo "the artifact version ${artifactVersion}"
	sh "mv ${projectProperties.buildInfo.artifactId}-${artifactVersion}.${projectProperties.deployInfo.artExtension} ${projectProperties.buildInfo.artifactId}.${projectProperties.deployInfo.artExtension}"
    sh "ls -ltr"
}
