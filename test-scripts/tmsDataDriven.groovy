import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field

import groovy.xml.*
import groovy.util.*
import groovy.json.*
//'sdtmsapp02v','sdtmsapp03v','sdtmsapp04v

@Library('pipeline-shared-library') _

//////////////////////////
// Global Parameters
//////////////////////////
repositoryId = 'maven-release'
def gitCredentials = 'scm-incomm'
def artifactloc = "${env.WORKSPACE}"
def env_propertyName = 'ART_VERSION'
// def folderBuildName = 'null'
// def folderEnvLoc = 'TARGET_ENVIRONMENT.split('-')[0]'
credsName = "scm_deployment"

@Field projectProperties

//////////////////////////
// User Input
//////////////////////////
//gitRepository = "${GIT_REPOSITORY}"
gitRepository = "https://github.com/InComm-Software-Development/cfes-tms-fraudmonitoringv2.git"
userInput = "${BUILD_TYPE}"
targetEnvironment = "${TARGET_ENVIRONMENT}"


//@Need to Revamp
targets = [
//'10.42.17.241',
    'dev': ['sdtmsapp01v', 'sdtmsapp02v','sdtmsapp03v','sdtmsapp04v'],
	'qa': ['sqltmsapp01v','sqltmsapp02v','sqs00tmsapp01v','sqs00tmsapp02v'],
	'prod': ['10.40.5.104', '10.40.5.108', '10.40.5.110', '10.40.5.111', '10.40.5.112', '10.40.5.113', '10.40.5.40', '10.40.5.41', '10.40.7.83', '10.40.7.84', '10.40.7.85', '10.40.7.86']

]

//srcProperties="src/main/resources/${folderEnvLoc}/"

relVersion = 'null'
currentBuild.result = 'SUCCESS'

approvalData = [
	'operators': "[ppiermarini,dstovall]",
	'adUserOrGroup' : 'ppiermarini,dstovall, awadhwa, howilliams',
    'target_env': '${targetEnvironment}' //Update to add env
]

artifactDeploymentLoc = ""
propertiesDeploymentLoc= ""
propArchive= ""
symlink = ""
emailDistribution= ""
runQualityGate = "${RUN_QUALITY_GATE}"
promoteArtifact = "${PROMOTE_ARTIFACT}"
gitBranch = "${BRANCH}"
now = new Date()
tStamp = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
archiveFolder="archive_${tStamp}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

user="jboss"
group="jboss"
chmod="750"

node('linux') {
    try {
        stage('Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }

        stage('Read YAML file') {
            echo "${projectProperties}"
         //   if(folderBuildName == null) {
                projectProperties = readYaml (file: 'project.yml')
         //   }
          //  else {
           //      projectProperties = readYaml (file: '${folderBuildName}/project.yml')

         //   }
            echo 'Reading project.yml file'
            if (projectProperties == null) {
                throw new Exception("project.yml not found in the project files.")
            }
            if (projectProperties.emailDistribution != null) {
                emailDistribution = projectProperties.emailDistribution
            }
        }

        stage('Set Java Version') {
            jdk = tool name:'openjdk-11.0.5.10'
            env.JAVA_HOME = "${jdk}"
            echo "jdk installation path is: ${jdk}"
            sh "${jdk}/bin/java -version"
        }

        stage('Build') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when ((userInput == 'Build') || (userInput == 'Release')) {
              //  springbuildreleaseV2(userInput, gitBranch, folderBuildName)
				springbuildrelease(userInput, gitBranch, maven)
            }
        }
        
        stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build') {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == "true") {
                   qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }

        stage('Approval') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Promote && promoteArtifact=${promoteArtifact}, should be true"
            when (userInput == 'Promote') {
                if (targetEnvironment.toUpperCase().contains("PROD") || targetEnvironment.toUpperCase().contains("STG")) {
                    getApproval(approvalData)       
                } else {
                    echo "Deploying to ${targetEnvironment} doesn't required any approvals"
                }
            }
        }

        //select the artifact
        stage('Get Artifact') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Promote"
            when (userInput == 'Promote') {
                getFromArtifactory()
            }
        }


        stage('Deployment') {
            echo "Evaluating the parameters for stage run: userInput=${userInput} should be Build or Promote, && promoteArtifact=${promoteArtifact} should be true"
            when ((userInput == 'Build' && promoteArtifact == "true") || (userInput == 'Promote')) {
                echo "Starting deployment Stage, userInput:${userInput}, promoteArtifact: ${promoteArtifact}"
                if (userInput == 'Build') {
                    targetEnvironment = projectProperties.deployment.defaultEnvironment
                    echo "Build always deployes to ${targetEnvironment}"
                }else if (userInput == 'Promote'){
                    promoteArtifact = "true"
                }
                echo "Setting Folder locations"
                setFolderLocations()
                echo "deploying to ${targetEnvironment}"
                deployComponents(targetEnvironment, targets[targetEnvironment], "${projectProperties.artifactId}-${relVersion}.${projectProperties.artifactExtension}")
            }
        }

    } catch (Exception e) {
        //stage('Notification'){
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.result = 'FAILURE'
    } finally {
		
		stage('Finalize') {
			//steps {
				echo """
********************************************************************************
Build-Node: ${NODE_NAME}
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
Build-Branch: ${BRANCH}
Environment: ${TARGET_ENVIRONMENT}
--------------------------------------------------------------------------------
"""

		script {
			if(emailDistribution.trim().length() > 0) {
				echo "send notification that a new artifact has been uploaded"

				mail 	to:"${emailDistribution}",
					subject:"${JOB_NAME} ${BUILD_NUMBER} ${TARGET_ENVIRONMENT}",
					body: """
A new build artifact has been created from the ${BRANCH} branch.

****************************************************
Build-Node: ${NODE_NAME}
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
Build-Branch: ${BRANCH}
Environment: ${TARGET_ENVIRONMENT}
**************************************************\n\n\n
"""
					}
				}
			//}
		}
	}
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls   ////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def deployComponents(String envName, targets, Artifact) {
    echo "my env= ${envName}"
    echo "Artifact Type: ${projectProperties.artifactExtension}"
	echo "Deployment location : ${projectProperties.deployment.deploymentLocation}"
	echo "Service Name: ${projectProperties.deployment.serviceName}"
	echo "Artifact ID: ${projectProperties.artifactId}"
	echo "Artifact : ${Artifact}"
	echo "Artifact Type: ${projectProperties.artifactId}"
    //credsName = "scm_deployment"

    //@Devops need to test symlink arguments for FSAPI Data Driven Deployment. May need to switch symlink depending on the service start/stop.

    symlink = "${projectProperties.artifactId}.${projectProperties.artifactExtension}"
	
    //@TODO: Update v2 deploy springboot functions with files
    def stepsInParallel = targets.collectEntries {
        ["$it" : { 
            springdeployV2(credsName, projectProperties.deployment.serviceName, it, Artifact, artifactDeploymentLoc, 
                propArchive, archiveFolder, propertiesDeploymentLoc, projectProperties.deployment.common, 
                projectProperties.deployment.commonLocation, symlink, srcProperties, user, group, chmod)

        }]
    }
    parallel stepsInParallel
}

def getFromArtifactory() {
    if (userInput == 'Promote') {
        echo 'Select an artifact from Artifacory'
        //repositoryId = 'maven-release'
        relVersion = getMyArtifact(repositoryId, projectProperties.groupId, projectProperties.artifactId, projectProperties.artifactExtension, '')

        echo "Selected Artifact=  ${projectProperties.artifactId}-${relVersion}.${projectProperties.artifactExtension}"
    } else {
        echo 'not promoting-Skipping'
    }
}


def setFolderLocations() {
    artifactDeploymentLocSet = projectProperties.deployment.artifactPropertyFolderName
    propertiesDeploymentLocSet = projectProperties.deployment.artifactPropertyFolderName
    propArchiveSet = projectProperties.deployment.artifactPropertyFolderName
    artifactDeploymentLoc = "/opt/${artifactDeploymentLocSet}/deploy"
    propertiesDeploymentLoc = "/opt/${propertiesDeploymentLocSet}/config"
    propArchive = "/opt/${propArchiveSet}/archive"

    echo "artifactDeploymentLoc: ${artifactDeploymentLoc}"
    echo "propertiesDeploymentLoc: ${propertiesDeploymentLoc}"
    echo "propArchive: ${propArchive}"


}
///// The End
