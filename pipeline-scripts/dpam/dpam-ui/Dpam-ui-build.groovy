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
NOTIFICATION_LIST = "ppiermarini@incomm.com"


TMP=""
TAG = "null"
WORKSPACE = "null"
//workspacePath = "SEED_JOB.lastBuild.checkouts[0].workspace"

pipeline {
	agent {
		label "npm"
	}

	parameters {
		listGitBranches name: 'BRANCH_NAME', branchFilter: 'refs/heads/(*.*)', defaultValue: 'develop', selectedValue: 'DEFAULT', type: 'PT_BRANCH_TAG', remoteURL: "${gitUrl}", credentialsId: 'scm-incomm', description: "create artifact from ${gitUrl}"
		booleanParam(name: 'ENABLE_BUILD', defaultValue: true, description: 'build a new artifact')
		//booleanParam(name: 'ENABLE_STATIC_ANALYSIS', defaultValue: false, description: 'run Sonar static analysis tool')
		choice(name: "targetEnv", choices: ['dev-01', 'dev-02', 'tst-01', 'tst-02', 'intg-01', 'intg-02', 'prod-01', 'prod-02'], description: "Environment to build for")
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
				dir ('project'){
					githubCheckout(gitCreds,gitUrl,BRANCH_NAME)
				}
					//echo "workspacePath is ${workspacePath}"
					TMP="${env.WORKSPACE}/tmp"
					WORKSPACE="${env.WORKSPACE}"
					echo "TMP is ${TMP}"
					echo "WORKSPACE is ${env.WORKSPACE}"
					TAG = BRANCH_NAME.split('/')
					TAG = "${TAG[2]}"
					echo "TAGS zero ${TAG[0]} one ${TAG[1]} two ${TAG[2]}"
					echo "${TAG}"
					npm_config_cache="${env.WORKSPACE}/npm-cache"
					// get current versions on develop, master and artifactory
					//DEVELOP_VERSION_NUMBER = getBranchVersionNumber("${gitUrl}", "develop")
					//MASTER_VERSION_NUMBER = getBranchVersionNumber("${gitUrl}", "master")
					//BRANCH_VERSION_NUMBER = getBranchVersionNumber("${gitUrl}", "${BRANCH_NAME}")
					//REPOSITORY_VERSION_NUMBER = getLatestVersionNumber("incomm-release", "${artifactGroup}", "${artifactId}")

					echo """
**************************************
BRANCH NAME: ${TAG[2]}
Github Repo: ${gitUrl}
**************************************\n\n
"""
				}
			}
		}//initialize

		stage('Build') {
			when {
				expression { params.ENABLE_BUILD == true }
			}

			steps {
				echo 'building project...'

				script {
					sh """
						
						mkdir -p "${TMP}"

						echo $PATH
						ls -ltr /bin
						ls -ltr /bin/node
						/bin/node --version
						/bin/npm --version
						#/bin/npm whoami

						echo "DEBUG 1"
						echo "$WORKSPACE"
						#export npm_config_cache=$WORKSPACE/npm-cache
						cd project || exit 1
						/bin/npm cache verify  || exit 1
						echo npm_config_cache=$npm_config_cache
						sleep 4
						echo
						echo Installing angular...
						#npm install @angular/cli@1.4.1  || exit 1
						cd "$WORKSPACE" || exit 1
						# Hack to work around proxy for now:  https://github.com/sass/node-sass/issues/1104
						# We pull the binding.node from our Artifactory.  We use wget with .wgetrc to handle
						# authentication with Artifactory.
						wget --no-verbose --no-check-cert https://maven.incomm.com/artifactory/scm/sass/node-sass/releases/v4.7.2/linux-x64-48_binding.node || exit 1
						cd project || exit 1
						/bin/npm install --sass-binary-path=$WORKSPACE/linux-x64-48_binding.node  @angular/cli@1.4.1 || exit 1
						/bin/npm run-script ng -v


						# Hack to work around proxy for now:  https://www.npmjs.com/package/phantomjs-prebuilt -> Downloading from custom url.
						echo ${user}
						echo Installing phantomjs-prebuilt
						# Hack to work around proxy.  We pull the package from our Artifactory using wget
						# and .wgetrc for credentials.
						cd "$WORKSPACE" || exit 1
						wget --no-verbose --no-check-cert https://maven.incomm.com/artifactory/scm/phantomjs/releases/download/v2.1.1/phantomjs-2.1.1-linux-x86_64.tar.bz2 || exit 1
						tar -xjf phantomjs-2.1.1-linux-x86_64.tar.bz2 || exit 1
						export PATH=$WORKSPACE/phantomjs-2.1.1-linux-x86_64/bin:$PATH


						cd project || exit 1
						/bin/npm install phantomjs-prebuilt
						/bin/npm run-script ng -v
						/bin/npm list

						echo "DEBUG"
						echo Running npm install...
						/bin/npm install  || exit 1
						npm rebuild --sass-binary-path=$WORKSPACE/linux-x64-48_binding.node node-sass || exit 1

						echo
						echo Rolling ng2-ckeditor from 1.1.13 to 1.1.9...
						/bin/npm install ng2-ckeditor@1.1.9  || exit 1

						echo
						echo Do the build...
						/bin/npm --sass-binary-path=$WORKSPACE/linux-x64-48_binding.node run-script ng  -- build --prod --environment=${targetEnv} --no-progress || exit 1


						echo
						echo Packaging files...
						tar -C dist -zcf ../package-${TAG}.tar . || exit 1
						md5sum ../package-${TAG}.tar
						ls -al ../package-${TAG}.tar


						echo
						echo Done building/packaging.
					"""

				}

				// sh 'mvn -DskipTests dependency:purge-local-repository clean install -U'
		//		sh 'mvn clean install -U -DskipTests'
				sh "ls -ltrh && pwd"
				sleep(3)

			}
		}//build stage




		stage('Upload Artifact') {
			when {
				expression { params.ENABLE_UPLOAD_ARTIFACT == true }
			}

			steps {
				echo 'deploying artifact to repository...'
				//sh 'mvn -DskipTests deploy'	
				sleep(5)
			}
		}

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