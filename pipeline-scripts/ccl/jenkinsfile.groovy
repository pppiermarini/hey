import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field

import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//////////////////////////
// Global Parameters
//////////////////////////
@Field def repositoryId = 'maven-release'
def gitCredentials = 'scm-incomm'
def artifactloc = "${env.WORKSPACE}"
def env_propertyName = 'ART_VERSION'
operators = "['psharma','vhari','mjoshi']"

@Field projectProperties

emailDistribution = 'vhari@incomm.com psharma@incomm.com'

//////////////////////////
// User Input
//////////////////////////
gitRepository = "${GIT_REPOSITORY}"
gitBranch = "${BRANCH}"
userInput = "${BUILD_TYPE}"
targetEnvironment = "${TARGET_ENVIRONMENT}"
testSuite = "${TEST_SUITE}"
runQualityGate = "${RUN_QUALITY_GATE}"
promoteArtifact="${PROMOTE_ARTIFACT}"

targets = [
    'dev-a': ['sdcclappa01v.unx.incommtech.net'],
    'dev-b': ['sdcclappb01v.unx.incommtech.net'],
    'qa-a': ['sqcclappa01v.unx.incommtech.net'],
    'qa-b': ['sqcclappb01v.unx.incommtech.net'],
    'qa-c': ['sqlcclappc01v.unx.incommtech.net'],
    'qa-d': ['sqlcclappd01v.unx.incommtech.net'],
    'qa-int': ['sqicclapp01v.unx.incommtech.net'],
    'qa-int-2': ['sqlicclaspp02v.unx.incommtech.net'],
    'uat-ui'  : ['succlui01v.unx.incommtech.net'],
    'uat-ui-2'  : ['sulcclui02v.unx.incommtech.net'],
    'uat-2'  : ['succlapp02v.unx.incommtech.net'],
    'uat-3'  : ['succlapp03v.unx.incommtech.net'],
    'uat-4'  : ['succlapp04v.unx.incommtech.net'],
    'uat-5'  : ['sulcclapp05v.unx.incommtech.net'],
    'uat-6'  : ['sulcclapp06v.unx.incommtech.net'],
    'uat-7'  : ['sulcclapp07v.unx.incommtech.net'],

    'STG-APP-01': ['sscclapp01fv.fastcard.local'],
    'STG-CSS-01' : ['sscclapp02fv.fastcard.local'],
    'STG-HZ-01' : ['sscclhz01fv.fastcard.local'],
    'STG-CONFIG-01' : ['sscclui02fv.fastcard.local'],
    'STG-UI-01' : ['sscclui01v'],

    'PROD-APP-01': ['spcclapp01fv.fastcard.local'],
    'PROD-APP-02': ['spcclapp02fv.fastcard.local', 'spcclapp03fv.fastcard.local', 'spcclapp04fv.fastcard.local'],
    'PROD-APP-03': ['spcclapp05fv.fastcard.local', 'spcclapp06fv.fastcard.local', 'spcclapp07fv.fastcard.local'],
    'PROD-CONFIG-01' : ['spcclui01fv.fastcard.local'],
    'PROD-CONFIG-02' : ['spcclui02fv.fastcard.local', 'spcclui03fv.fastcard.local'],
    'PROD-JOB-01' : ['spcclui03fv.fastcard.local'],
    'PROD-HZ-01' : ['spcclhz01fv.fastcard.local'],
    'PROD-HZ-02' : ['spcclhz02fv.fastcard.local', 'spcclhz03fv.fastcard.local'],
    'PROD-CSS-01' : ['spcclcss01fv.fastcard.local'],
    'PROD-CSS-02' : ['spcclcss02fv.fastcard.local', 'spcclcss03fv.fastcard.local', 'spcclcss04fv.fastcard.local'],

    'PROD-UI-01' : ['spcclui01v'],
    'PROD-UI-02' : ['spcclui02v', 'spcclui03v']
]

relVersion = 'null'
currentBuild.result = 'SUCCESS'

approvalData = [
	'operators': "[ppiermarini,vhari,psharma,mjoshi]",
	'adUserOrGroup' : 'ppiermarini,vhari,psharma,mjoshi',
]

node('linux') {
    try {
        stage('Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out code from SCM'
            githubCheckout(gitCredentials, gitRepository, gitBranch)
        }

        stage('Read YAML file') {
            echo 'Reading project.yml file'
            echo "QualityGate run set to ${runQualityGate}"
            projectProperties = readYaml (file: 'project.yml')
            if (projectProperties == null) {
                throw new Exception("project.yml not found in the project files.")
            }
            echo "${projectProperties}"
            if (projectProperties.emailDistribution != null) {
                emailDistribution = projectProperties.emailDistribution
            }
        }

        stage('Set Java Version') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should not be Promote"
            when (userInput != 'Promote' ) {
                echo "Setting java environment: ${projectProperties.javaVersion}"
                if (projectProperties.javaVersion == 11) {
                    jdk = tool name:'openjdk-11.0.5.10'
                    env.JAVA_HOME = "${jdk}"
                    echo "jdk installation path is: ${jdk}"
                    sh "${jdk}/bin/java -version"
                } else {
                    echo 'Using default java version'
                }
            }
        }

        stage('Build') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
                cclbuildrelease(userInput, gitBranch, projectProperties.artifactId, projectProperties.artifactExtension)
            }
        }

        stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == 'true') {
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
            when ((userInput == 'Build' && promoteArtifact == 'true') || (userInput == 'Promote')) {
                echo "Starting deployment Stage, userInput:${userInput}, promoteArtifact: ${promoteArtifact}"
                if (userInput == 'Build') {
                    targetEnvironment = projectProperties.deployment.defaultEnvironment
                    echo "Build always deployes to ${targetEnvironment}"
                }else if (userInput == 'Promote'){
                    promoteArtifact = true
                }
                echo "deploying to ${targetEnvironment}"
                deployComponents(targetEnvironment, targets[targetEnvironment], "${projectProperties.artifactId}-${relVersion}.${projectProperties.artifactExtension}")
            }
        }

        stage('QA Automation') {
            echo "Evaluating the parameters for stage run: userInput=${userInput} should not be Release && testSuite=${testSuite} should not be none && projectProperties has test parameters"
            when (userInput != 'Release' && projectProperties.test != null && projectProperties.test.run == true && testSuite != 'none') {
                node('QAAutomation') {
                    echo "Checking test parameter from project: ${projectProperties.test}"
                    cleanWs()
                    echo "Running tests with ${projectProperties.test.gitRepository}, ${projectProperties.test.gitBranch}"
			testworkspace = ${projectProperties.test.workspaceLocation}
                    def workspaceFolder = "${WORKSPACE}/${testworkspace}"
                    echo "Test run workspace ####################### ${workspaceFolder}"
                    dir("${workspaceFolder}"){
                        githubCheckout(gitCredentials, projectProperties.test.gitRepository, projectProperties.test.gitBranch)
                        ccltestrun(testSuite, workspaceFolder, ReadyAPI_License, ReadyAPI_TestRunner, targetEnvironment, emailDistribution)
                    }
                    
                }
            }
        }
    } catch (Exception e) {
        //stage('Notification'){
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.result = 'FAILURE'
    } finally {
        if (currentBuild.result == 'FAILURE') {
            echo 'Build failed'
            emailext attachLog: true, body: "${env.BUILD_URL}", subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}", recipientProviders: [culprits()], mimeType: 'text/html', to: "${emailDistribution}"

        }else if (currentBuild.result == 'SUCCESS') {
            echo 'Build is success'
            emailext attachLog: true, body: "${env.BUILD_URL}", subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}", recipientProviders: [culprits()], mimeType: 'text/html', to: "${emailDistribution}"
        }
    }
} //end of node

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
	
    def stepsInParallel = targets.collectEntries {
        ["$it" : { ccldeploy(projectProperties.deployment.type, it, projectProperties.deployment.deploymentLocation, Artifact, projectProperties.deployment.serviceName, projectProperties.deployment.tempLocation, projectProperties.deployment.dataLocation, projectProperties.artifactId, projectProperties.artifactExtension) }]
    }
    parallel stepsInParallel
}

def getFromArtifactory() {
    if (userInput == 'Promote') {
        echo 'Select an artifact from Artifacory'

        relVersion = getMyArtifact(repositoryId, projectProperties.groupId, projectProperties.artifactId, projectProperties.artifactExtension, '')

        echo "Selected Artifact=  ${projectProperties.artifactId}-${relVersion}.${projectProperties.artifactExtension}"
    } else {
        echo 'not promoting-Skipping'
    }
}
///// The End
