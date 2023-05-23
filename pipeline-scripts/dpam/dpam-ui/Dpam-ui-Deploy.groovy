import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


gitUrl = "https://github.com/InComm-Software-Development/sop-dpam-ui.git"
gitCreds="scm-incomm"
user="apache"
group="apache"
chmod="750"
credsName = "scm_deployment"

NOTIFICATION_LIST = "ppiermarini@incomm.com angeorge@incomm.com"

String myjson = ""
artifactDeploymentLoc ="/var/www/html/dpam"
httpConf="/etc/httpd/conf"
httpConfd="/etc/httpd/conf.d"
configFolderLoc="configuration"
tmpFolder="/tmp"
serviceName="httpd"
artifactName="null"

htaccess=".htaccess"

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
//sqdpamapp01v.unx.incommtech.net  10.42.80.246
//sqdpamapp02v.unx.incommtech.net  10.42.80.247
//spdpamapp01v
//etc/httpd/conf/httpd.conf
//etc/httpd/conf.d/ssl.conf
//etc/httpd/conf.d/autoindex.conf
//etc/httpd/conf.d/welcome.conf
//var/www/html/dpam/.htaccess

approvalData = [
	'operators': "[ppiermarini,angeorge,glindsey]",
	'adUserOrGroup' : 'ppiermarini,angeorge,glindsey',
	'target_env' : "${targetEnv}"
]

repoId = "maven-release"
groupId="com.incomm.dpam"
artifactId="dpam-ui"
//artifactVersion="VERSION"
artExtension="tar"
configEnv=""
displayBranch=""
targetList=[]
target_hostname=""

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

					myjson = readFile 'package.json'
					jsonObj = readJSON text: myjson
					VERSION="${jsonObj.version}"
					echo "Version IS $VERSION"
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
				echo 'building project...'

				script {
					artifactName="dpam-ui-${VERSION}.tar"
					
					if (VERSION.contains('SNAPSHOT')){
						echo " SNAPSHOT "
						sh "/bin/wget --no-check-certificate https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/dpam/dpam-ui/${VERSION}/dpam-ui-${VERSION}.tar"
					} else {
						echo "RELEASE"
						sh "/bin/wget --no-check-certificate https://maven.incomm.com/artifactory/incomm-release/com/incomm/dpam/dpam-ui/${VERSION}/dpam-ui-${VERSION}.tar"
					} 
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
					deployPropertyFiles(targetEnv, targets[targetEnv], "dpam-ui-${VERSION}.tar")
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
					deployComponents(targetEnv, targets[targetEnv], "dpam-ui-${VERSION}.tar")
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
					deployComponents(targetEnv, targets[targetEnv], "dpam-ui-${VERSION}.tar")
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
Artifact Name:     		dpam-ui-${VERSION}.tar
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
dpam-ui version ${VERSION} has been deployed from the ${displayBranch} branch.


**************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
Deployment-Branch: 		${displayBranch}
Artifact Name:     		dpam-ui-${VERSION}.tar
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
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && /bin/systemctl stop ${serviceName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf "${artifactDeploymentLoc}/"'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mkdir -pv ${artifactDeploymentLoc}'
			cp -p ${configFolderLoc}/${configEnv}/${htaccess} .
			ls -ltra
			scp -q -o StrictHostKeyChecking=no ${htaccess} root@${target_hostname}:${artifactDeploymentLoc}
			scp -q -o StrictHostKeyChecking=no dpam-ui-${VERSION}.tar root@${target_hostname}:${artifactDeploymentLoc}
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'tar -C ${artifactDeploymentLoc} -xf ${artifactDeploymentLoc}/dpam-ui-${VERSION}.tar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'chown -R ${user}:${group} ${artifactDeploymentLoc} && chmod ${chmod} ${artifactDeploymentLoc}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'find ${artifactDeploymentLoc}/ -type d -exec chmod 750 \'{}\' \\;'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'find ${artifactDeploymentLoc}/ -type f -exec chmod 640 \'{}\' \\;'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && /bin/systemctl restart ${serviceName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rvf ${artifactDeploymentLoc}/dpam-ui-${VERSION}.tar'
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