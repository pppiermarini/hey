




@Library('pipeline-shared-library') _

imageLabel = "hello-world"
gitBranch = "development"
gitRepository = "https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitCreds="scm-incomm"
NOTIFICATION_LIST = "ppiermarini@incomm.com rkale@incomm.com"


pipeline {
	agent {
		label "linux"
	}
	
	parameters {
		listGitBranches name: 'gitBranch', branchFilter: 'refs/heads/(*.*)', defaultValue: 'development', selectedValue: 'DEFAULT', type: 'PT_BRANCH_TAG', remoteURL: "${gitRepository}", credentialsId: 'scm-incomm', description: "create artifact from ${gitRepository}"
		booleanParam(name: 'ENABLE_BUILD', defaultValue: true, description: 'build a new artifact')
		//booleanParam(name: 'ENABLE_STATIC_ANALYSIS', defaultValue: false, description: 'run Sonar static analysis tool')
		choice(name: "targetEnv", choices: ['dev', 'tst', 'intg-01', 'prod-01'], description: "Environment to build for")
////	booleanParam(name: 'ENABLE_BUILD_DOCKER_IMAGES', defaultValue: false, description: 'create, tag and upload new docker images containing the new artifact to https://docker.maven.incomm.com')
		//booleanParam(name: 'ENABLE_UPLOAD_ARTIFACT', defaultValue: true, description: 'upload the new artifact to the artifact repository on https://maven.incomm.com')
		//string(name: 'NOTIFICATION_LIST', defaultValue: '', description: 'semi-colon (;) delimited list of email addresses to notify the result of this job to (notifications sent on successful run only)')
	}




	options {
		disableConcurrentBuilds()
	}

	tools {
		maven 'maven321'
		jdk 'jdk1.8.0_202'
	}

	stages {

    	stage('Github Checkout') {
			steps{
				echo 'Cleaing workspace'
				cleanWs()
				echo 'Checking out code from SCM'
				githubCheckout(gitCreds, gitRepository, gitBranch)
			}
        }
	
		
    	stage('Read YAML file') {
            
			steps{
				script{
					echo 'Reading dataDrivenDocker.yml file'
					projectProperties = readYaml (file: 'test-yamls/dataDrivenDocker.yml')
					if (projectProperties == null) {
						throw new Exception("dataDrivenDocker.yml not found in the project files.")
					}
					echo "${projectProperties}"
					if (projectProperties.email.emailDistribution != null) {
						emailDistribution = projectProperties.email.emailDistribution
					}
				}
			}
        }
		
        //Call to Docker Registry based on the imagename
        stage('Call to Docker Artifactory') {
			steps{
				script{
					if (projectProperties.imageInfo.imageName != null) {
					allSnaps = callDockerRegistry(projectProperties.imageInfo.imageName)
					}
				}
			}
        }


				  
        //API call to Docker Registry for userinput of the tag
        stage('Choose a tag') {
			steps{
				script{
				imageLabel = getImageTag(imageLabel, allSnaps)
				echo "${projectProperties.imageInfo.imageName}:${imageLabel}"
				}
			}
        }
		
		
		stage('Build') {
			when {
				expression { params.ENABLE_BUILD == true }
			}

			steps {
				echo 'building project...'

				script {
					withSonarQubeEnv('sonarqube.incomm.com'){
						sh "mvn -version"
					} 
				}

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
Build-Branch: ${gitBranch}
--------------------------------------------------------------------------------
"""

		script {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that a new artifact has been uploaded"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """
A new build artifact has been created from the ${gitBranch} branch.

**************************************************
Build-Node: ${NODE_NAME}
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
Build-Branch: ${gitBranch}
**************************************************\n\n\n
"""
					}
				}
			}
		}
	} //stages

} //pipeline
