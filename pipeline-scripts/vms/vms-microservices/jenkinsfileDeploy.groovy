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
def env_propertyName = 'ART_VERSION'
operators = "['ppattabiraman','vhari']"
repositoryId = 'maven-release'
groupId = 'com.incomm.vms'
artifactExtension = 'jar'
artifactName = ''
gridurl = 'http://10.42.84.59:4444/wd/hub'
emailDistribution = 'vhari@incomm.com'
maven="/opt/apache-maven-3.2.1/bin/mvn"

//////////////////////////
// User Input From Jenkins
//////////////////////////
gitRepository = "https://github.com/InComm-Software-Development/VMS_MICROSERVICES.git"
gitBranch = "${QA_BRANCH}"
userInput = "${BUILD_TYPE}"
targetEnvironment = "${TARGET_ENVIRONMENT}"
testSuite = "${TEST_SUITE}"
artifactId = "${Select_Service}"
publisttestreport="${Upload_Test_Report}"

targets = [
    'dev': ['10.42.16.191'],
    'dev-b': [''],
    'qa-a': [''],
    'qa-b': [''],
    'uat'  : [''],
    'stg': [''],
    'PROD-POOL1': [''],
    'PROD-POOL2': [''],
    'PROD-POOL3': [''],
]

relVersion = 'null'
currentBuild.result = 'SUCCESS'

node('linux') {
    try {
       
        stage('Approval') {
            echo "Valid for only STG and PROD deployments"
            // JNOTES: Old approval
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
        // JNOTES: userInput is a dropdown item configured in Jenkins already
        // JNOTES: We would have to add a new item each time they added a microservice anyway
        stage('Get Artifact') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Promote"
            when (userInput == 'Promote') {
                getFromArtifactory()
            }
        }

        stage('Deployment') {
            when (userInput == 'Promote') {
                echo "deploying to ${targetEnvironment}"
                deployComponents(targetEnvironment, targets[targetEnvironment], "${artifactId}-${relVersion}.${artifactExtension}")
            }
        }

        stage('QA Automation') {
            echo "Evaluating the parameters for stage run: userInput=${userInput} should not be Release && testSuite=${testSuite} should not be none && projectProperties has test parameters"
            when ((userInput == 'Promote' && runAtuomatedTests == 'true') || (userInput == 'Test')) {
                node('QAAutomation') {
                    cleanWs()
                        githubCheckout(gitCredentials, gitRepository, gitBranch)
                    //vmstestrun(testSuite, workspaceFolder, targetEnvironment, emailDistribution)
                        echo "test suit selected ${testSuite}"
                        echo "Running tests on  ${targetEnvironment}"
                        echo "Grid URL ${gridurl}"
                        echo "PublishReport set to ${publisttestreport}"
                        sh "${maven} clean install -Dsuite=$testSuite -DrunMode=grid -Denv=$targetEnvironment -DgridURL=$gridurl -Dheadless=true -Dconfluence.publishEnabled=$publisttestreport"
                        publishHTML(target: [
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: true,
							reportDir: "reports/html_report/",
							reportFiles: '*.html',
							reportName: "HTML Report",
							reportTitles: 'HTML Report'
						])
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
    def serviceName = "${artifactId}"
    echo "my env= ${envName}"
    echo "Artifact Type: ${artifactExtension}"
    echo "Service Name: ${serviceName}"
    def stepsInParallel = targets.collectEntries {
        // JNOTES: Deployment is already standardized
        ["$it" : { vmsdeploy(it, Artifact, serviceName, artifactId) }]
    }
    parallel stepsInParallel
}

def getFromArtifactory() {
    if (userInput == 'Promote') {
        echo 'Select an artifact from Artifacory'

        //relVersion = getMyArtifact(repositoryId, groupId, artifactId, artifactExtension, '')
        def artifactVersion = input message: '', parameters: [[$class: 'VersionParameterDefinition', artifactid: artifactId, description: '', groupid: groupId, repoid: repositoryId]]
        artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artifactExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
    } else {
        echo 'not promoting-Skipping'
    }
}
///// The End