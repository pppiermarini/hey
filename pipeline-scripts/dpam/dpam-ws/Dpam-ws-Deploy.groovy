import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


gitUrl = "https://github.com/InComm-Software-Development/sop-dpam-ws.git"
gitCreds="scm-incomm"
user="tcserver"
group="pivotal"
chmod="750"
credsName = "scm_deployment"

NOTIFICATION_LIST = "ppiermarini@incomm.com angeorge@incomm.com"

String myjson = ""
artifactDeploymentLoc ="/var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/webapps"
instanceName="dpam-ws-api"
instanceConf="/var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/conf"
instanceLib="/var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib"
instanceBin="/var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/bin"
instanceLogs = "/var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/logs"
srcConfigFolderLoc="configuration"

serviceName="dpam-ws-instance"
artifactName="null"



ENV=""
TMP=""
TAG = "null"
WORKSPACE = "null"

targets = [
	'dev-01':	['10.42.16.67'],
	'dev-02':	['10.42.16.120'],
	'tst-01':  ['10.42.80.246'],
	'tst-02':  ['10.42.80.247'],
	'intg-01':  ['10.42.32.11'],
	'intg-02':  ['10.42.32.12'],
	'prod-01':  ['10.40.7.60'],
	'prod-02':  ['10.40.7.61'],
]




approvalData = [
	'operators': "[ppiermarini,angeorge,glindsey]",
	'adUserOrGroup' : 'ppiermarini,angeorge,glindsey',
	'target_env' : "${targetEnv}"
]

repoId = "maven-all"
groupId = "com.incomm.dpam.api"
artifactId = "dpam-ws-api"
//env_propertyName = 'ART_VERSION'
artExtension = "war"
artifactName = ""

configEnv=""
displayBranch=""
targetList=[]
target_hostname=""

ArtifactVersion = ""
list = ""

pipeline {
	agent {
		label "npm"
	}

	parameters {
		listGitBranches name: 'BRANCH_NAME', branchFilter: 'refs/heads/(*.*)', defaultValue: 'develop', selectedValue: 'DEFAULT', type: 'PT_BRANCH_TAG', remoteURL: "${gitUrl}", credentialsId: 'scm-incomm', description: "create artifact from ${gitUrl}"
		//text(name: 'BRANCH_NAME', defaultValue: 'master', description: 'Enter the branch name')
		booleanParam(name: 'ENABLE_PROPERTIES_DEPLOY', defaultValue: false, description: 'Select to deploy property files')
		booleanParam(name: 'ENABLE_PROD_DEPLOY', defaultValue: false, description: 'Select when deploying to production')
		choice(name: "targetEnv", choices: ['dev-01', 'dev-02', 'tst-01', 'tst-02', 'intg-01', 'intg-02', 'prod-01', 'prod-02'], description: "Environment to deploy to")
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
					//git 	url: "${gitUrl}",
					//branch: "${params.BRANCH_NAME}",
					//credentialsId: 'scm-incomm'
					
				script {
					githubCheckout(gitCreds,gitUrl,BRANCH_NAME)

					//myjson = readFile 'package.json'
					//jsonObj = readJSON text: myjson
					//VERSION="${jsonObj.version}"
					//echo "Version IS $VERSION"
					ENV = targetEnv.split('-');
					configEnv = ENV[0];
					
					TMP="${env.WORKSPACE}/tmp"
					WORKSPACE="${env.WORKSPACE}"
					echo "TMP is ${TMP}"
					echo "WORKSPACE is ${env.WORKSPACE}"
					echo "BRANCH_NAME $BRANCH_NAME"
					
					
					if (BRANCH_NAME.contains('/')){
						echo "SPLIT BRANCH NAME"
					displayBranch = BRANCH_NAME.split('/')
					displayBranch = "${displayBranch[2]}"
					} else {
						echo "NOT SPLIT BRANCH NAME"
						displayBranch = "${BRANCH_NAME}"
					}
					

					echo """
**************************************
BRANCH NAME: ${BRANCH_NAME}
Github Repo: ${gitUrl}
**************************************\n\n
"""
				}
			}
		}//initialize

		stage('Approval Check') {
			when {
				expression { (params.ENABLE_PROD_DEPLOY == true) && (configEnv == "prod") }
			}
			
			steps{
				script{
					approveResult = getApproval(approvalData)
					echo "Approval Result: ${approveResult}"
				}
			}
		}

		stage('Get Artifact') {
			//when {
			//	expression { params.ENABLE_DEPLOY == true }
			//}

			steps {
				echo 'getting the artifact...'

				script {
					
					list = artifactResolverV2(repoId, groupId, artifactId, artExtension)

					//echo "the list contents ${list}"

					artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
					parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
					sleep(3)
					artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
					echo "the artifact version ${artifactVersion}"
					sh "ls -ltr"

				}

			}
		}//Get Artifact stage

		stage('Property file Demployment') {
			when {
				expression { (params.ENABLE_PROPERTIES_DEPLOY == true) }
			}

			steps {
				echo 'Deploying property files...'
				script {
					echo "deploying code....."
					deployPropertyFiles(targetEnv, targets[targetEnv], "${artifactId}-${artifactVersion}.${artExtension}")
				}

			}
		}//property file stage


		stage('Lower Env Demployment') {
			when {
				expression { (params.ENABLE_PROD_DEPLOY == false) && (configEnv != "prod") }
			}

			steps {
				echo 'Deploying to Lower environment...'
				script {
					echo "deploying code....."
					deployComponents(targetEnv, targets[targetEnv], "${artifactId}-${artifactVersion}.${artExtension}")
				}

			}
		}//deployment stage
		
		stage('Production Env Demployment') {
			when {
				expression { (params.ENABLE_PROD_DEPLOY == true) && (configEnv == "prod") }
			}

			steps {
				echo 'Deploying to Production environment...'
				script {
					echo "deploying code....."
					deployComponents(targetEnv, targets[targetEnv], "${artifactId}-${artifactVersion}.${artExtension}")
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
Artifact Name:     		dpam-ws-api-${artifactVersion}.tar
Environment:			${targetEnv}
Configs:				${ENABLE_PROPERTIES_DEPLOY}
targets:				${targetList}
--------------------------------------------------------------------------------
"""

		script {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that deployment has taken place"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """
dpam-ws-api version ${artifactVersion} has been deployed from the ${displayBranch} branch.


**************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
Deployment-Branch: 		${displayBranch}
Artifact Name:     		dpam-ws-api-${artifactVersion}.tar
Environment:			${targetEnv}
Configs:				${ENABLE_PROPERTIES_DEPLOY}
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
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact, envName) } ]
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


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	echo " the target is: ${target_hostname}"
targetList << target_hostname
	sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${instanceName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -f ${artifactDeploymentLoc}/${instanceName}.${artExtension}'
			scp -q -o StrictHostKeyChecking=no ${artifactId}-${artifactVersion}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}/${artifactId}.${artExtension}
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'chown -R ${user}:${group} ${artifactDeploymentLoc} && chmod ${chmod} ${artifactDeploymentLoc}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}
}
//httpConf="/etc/httpd/conf"
//httpConfd="/etc/httpd/conf.d"

def deployprops(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
			echo "libs"
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/mdm-client.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/mdm-client.properties root@${target_hostname}:${instanceLib}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/dpam-logback.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/dpam-logback.properties root@${target_hostname}:${instanceLib}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/dpam-email.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/dpam-email.properties root@${target_hostname}:${instanceLib}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/dpam-db.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/dpam-db.properties root@${target_hostname}:${instanceLib}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/dpam-auth.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/dpam-auth.properties root@${target_hostname}:${instanceLib}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/dpam-app.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/dpam-app.properties root@${target_hostname}:${instanceLib}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/lib/dpam-azure.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/lib/dpam-azure.properties root@${target_hostname}:${instanceLib}; fi
			echo "conf"
			if [ -e ${srcConfigFolderLoc}/${configEnv}/conf/catalina.properties ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/conf/catalina.properties root@${target_hostname}:${instanceConf}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/conf/server.xml ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/conf/server.xml root@${target_hostname}:${instanceConf}; fi
			if [ -e ${srcConfigFolderLoc}/${configEnv}/conf/dpam-ws.keystore ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/conf/dpam-ws.keystore root@${target_hostname}:${instanceConf}; fi
			echo "bin"
			if [ -e ${srcConfigFolderLoc}/${configEnv}/bin/setenv.sh ]; then scp -q -o StrictHostKeyChecking=no ${srcConfigFolderLoc}/${configEnv}/bin/setenv.sh root@${target_hostname}:${instanceBin}; fi

			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'chown -R ${user}:${group} ${instanceConf}/
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'find ${artifactDeploymentLoc}/ -type d -exec chmod 750 \'{}\' \\;'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'find ${artifactDeploymentLoc}/ -type f -exec chmod 640 \'{}\' \\;'
		"""
	}
}
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib/mdm-client.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib/dpam-logback.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib/dpam-email.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib/dpam-db.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib/dpam-auth.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/lib/dpam-app.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/bin/setenv.sh
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/conf/catalina.properties
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/conf/server.xml
//var/opt/pivotal/pivotal-tc-server-standard/dpam-ws-instance/conf/dpam-ws.keystore (Only for INTG & Prod)
//No need to copy dpam-ws.crt.

