import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


gitUrl = "https://github.com/InComm-Software-Development/ddc-merchants.git"
gitCreds="scm-incomm"
NOTIFICATION_LIST = "ppiermarini@incomm.com"


TMP=""
TAG = "null"
WORKSPACE = "null"
//workspacePath = "SEED_JOB.lastBuild.checkouts[0].workspace"

pipeline {
	agent {
		label "linux2"
	}

	parameters {
		listGitBranches name: 'BRANCH_NAME', branchFilter: 'refs/heads/(*.*)', defaultValue: 'develop', selectedValue: 'DEFAULT', type: 'PT_BRANCH_TAG', remoteURL: "${gitUrl}", credentialsId: 'scm-incomm', description: "create artifact from ${gitUrl}"
		//booleanParam(name: 'ENABLE_BUILD', defaultValue: true, description: 'build a new artifact')
		//booleanParam(name: 'ENABLE_STATIC_ANALYSIS', defaultValue: false, description: 'run Sonar static analysis tool')
		//choice(name: "targetEnv", choices: ['dev-01', 'dev-02', 'tst-01', 'tst-02', 'intg-01', 'intg-02', 'prod-01', 'prod-02'], description: "Environment to build for")
////	booleanParam(name: 'ENABLE_BUILD_DOCKER_IMAGES', defaultValue: false, description: 'create, tag and upload new docker images containing the new artifact to https://docker.maven.incomm.com')
		//booleanParam(name: 'ENABLE_UPLOAD_ARTIFACT', defaultValue: true, description: 'upload the new artifact to the artifact repository on https://maven.incomm.com')
		//string(name: 'NOTIFICATION_LIST', defaultValue: '', description: 'semi-colon (;) delimited list of email addresses to notify the result of this job to (notifications sent on successful run only)')
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
				//git 	url: "${gitUrl}",
				//	branch: "${params.BRANCH_NAME}",
				//	credentialsId: 'scm-incomm'
				
				
				script {
					cleanWs()
				//sh "mkdir project"
				//dir ('project'){
					githubCheckout(gitCreds,gitUrl,BRANCH_NAME)
				//}

					echo """
**************************************
BRANCH NAME: ${BRANCH_NAME}
Github Repo: ${gitUrl}
**************************************\n\n
"""
				}
			}
		}//initialize

		stage('Build') {
			//when {
			//	expression { params.ENABLE_BUILD == true }
			//}

			steps {
				echo 'building project...'

				script {
					sh """
						mvn clean deploy -U -DskipTests

					"""
				}

				sh "ls -ltrh && pwd"
				sleep(3)

			}
		}//build stage




//		stage('Upload Artifact') {
//			when {
//				expression { params.ENABLE_UPLOAD_ARTIFACT == true }
//			}
//
//			steps {
//				echo 'deploying artifact to repository...'
//				//sh 'mvn -DskipTests deploy'	
//				sleep(5)
//			}
//		}

		stage('Finalize') {
			steps {
				echo """
********************************************************************************
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
Build-Branch: ${BRANCH_NAME}
--------------------------------------------------------------------------------
"""

		script {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that a new artifact has been uploaded"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """
A new build artifact has been created from the ${BRANCH_NAME} branch.

**************************************************
Build-Node: ${NODE_NAME}
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
Build-Branch: ${BRANCH_NAME}
**************************************************\n\n\n
"""
					}
				}
			}
		}
	}
}