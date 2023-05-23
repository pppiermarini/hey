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
def RUN_QUALITY_GATE = 'false'

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
    'qa-c': ['sqlcclappc01v.unx.incommtech.net'],
    'qa-d': ['sqlcclappd01v.unx.incommtech.net'],
    'qa-int': ['sqicclapp01v.unx.incommtech.net'],
    'uat-2'  : ['succlapp02v.unx.incommtech.net'],
    'uat-3'  : ['succlapp03v.unx.incommtech.net'],
    'uat-4'  : ['succlapp04v.unx.incommtech.net'],
    'uat-5'  : ['sulcclapp05v.unx.incommtech.net'],
    'uat-6'  : ['sulcclapp06v.unx.incommtech.net'],
    'uat-ui-2'  : ['sulcclui02v.unx.incommtech.net'],
    'stg': ['sscclapp01fv.fastcard.local', 'sscclapp02fv.fastcard.local'],
    'PROD-POOL1': ['spcclapp01fv.fastcard.local'],
    'PROD-POOL2': ['spcclapp02fv.fastcard.local', 'spcclapp03fv.fastcard.local', 'spcclapp04fv.fastcard.local'],
    'PROD-POOL3': ['spcclapp05fv.fastcard.local', 'spcclapp06fv.fastcard.local', 'spcclapp07fv.fastcard.local'],
]

relVersion = 'null'
currentBuild.result = 'SUCCESS'

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
                if (( targetEnvironment == 'PROD-POOL1') || (targetEnvironment == 'PROD-POOL2') || (targetEnvironment == 'PROD-POOL3' )) {
                    echo "Deploying to ${targetEnvironment} requires approval"
                    def Deployment_approval = input message: 'Deploying to FCV', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'psharma', submitterParameter: 'approver'
                    echo "${Deployment_approval}"
                    approval_status = "${Deployment_approval['approval_status']}"
                    def operator = "${Deployment_approval['approver']}"
                    String op = operator.toString()
                    if (approval_status == 'Proceed') {
                        echo "Operator is ${operator}"
                        if (operators.contains(op)) {
                            echo "${operator} is allowed to deploy into ${targetEnvironment}"
                        }
                        else {
                            throw new Exception('Throw to stop pipeline as user not in approval list')
                        }
                    }else {
                        throw new Exception('Throw to stop pipeline as user selected abort')
                    }
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
                    workspaceFolder = projectProperties.test.workspaceLocation
                    dir("${workspaceFolder}"){
                        githubCheckout(gitCredentials, projectProperties.test.gitRepository, projectProperties.test.gitBranch)
                    }
                    ccltestrun(testSuite, workspaceFolder, ReadyAPI_License, ReadyAPI_TestRunner, targetEnvironment, emailDistribution)
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
            emailext attachLog: true, body: "${env.BUILD_URL}", subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}", mimeType: 'text/html', to: "${emailDistribution}"

        }else if (currentBuild.result == 'SUCCESS') {
            echo 'Build is success'
            emailext attachLog: true, body: "${env.BUILD_URL}", subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}", mimeType: 'text/html', to: "${emailDistribution}"
        }
    }
} //end of node

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls   ////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def deployComponents(String envName, targets, Artifact) {
    echo "my env= ${envName}"
    echo "Artifact Type: ${projectProperties.artifactExtension}"
    def stepsInParallel = targets.collectEntries {
        ["$it" : { ccldeploynew(projectProperties.artifactExtension, it, projectProperties.deployment.deploymentLocation, Artifact, projectProperties.deployment.serviceName, projectProperties.deployment.tempLocation, projectProperties.deployment.dataLocation, projectProperties.artifactId, projectProperties.artifactExtension) }]
    }
    parallel stepsInParallel
}

def getFromArtifactory() {
    if (userInput == 'Promote') {
        echo 'Select an artifact from Artifacory'

        relVersion = getMyArtifact(repositoryId, projectProperties.groupId, projectProperties.artifactId, projectProperties.artifactExtension, projectProperties.artifactId + '.' + projectProperties.artifactExtension)

        echo "Selected Artifact=  ${projectProperties.artifactId}-${relVersion}.${projectProperties.artifactExtension}"
    } else {
        echo 'not promoting-Skipping'
    }
}
///// The End