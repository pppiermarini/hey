/*
=====================================================
=============== PIPELINE REQUIREMENTS ===============
=====================================================

1. THINGS THAT MUST MATCH THE ARTIFACT-ID:
    - systedmd service name
    - name of the startup shell script


*/

import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field

import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


// JENKINS UI PARAMS
targetEnv = "${TARGET_ENVIRONMENT}"
singularDeploy = "${SINGULAR_DEPLOY}"

// Globals
approvalData = [
    'operators': "[bmekonnen, dstovall, ppiermarini, jrivett]",
    'adUserOrGroup' : 'bmekonnen, dstovall, ppiermarini, jrivett',
    'target_env' : "${targetEnv}"
]

targets = [
    'dev': ['10.42.16.191'],
    'dev-b': [''],
    'qaa': [''],
    'qab': ['10.42.83.20', '10.42.83.21', '10.42.83.22', '10.42.83.23'],
    'uat': ['10.42.49.48', '10.42.49.49', '10.42.49.98', '10.42.49.99', '10.42.49.114'],
    'stg': ['10.41.5.96', '10.41.5.97'],
    'splvmsaps101fv': ['10.41.7.230'],
    'splvmsaps102fv': ['10.41.7.231'],
    'splvmsaps103fv': ['10.41.7.232'],
    'splvmsaps104fv': ['10.41.7.233'],
    'splvmsaps105fv': ['10.41.7.234'],
    'splvmsaps106fv': ['10.41.7.235'],
    'splvmsaps107fv': ['10.41.7.236'],
    'splvmsaps108fv': ['10.41.7.237'],
    'splvmsaps109fv': ['10.41.7.238'],
    'splvmsaps110fv': ['10.41.7.239'],
    'splvmsaps114fv': ['10.41.7.243'],
    'splvmsaps115fv': ['10.41.7.244'],
    'splvmsaps116fv': ['10.41.7.245'],
    'splvmsaps117fv': ['10.41.7.246'],
    'splvmsaps118fv': ['10.41.7.247'],
    'splvmsaps119fv': ['10.41.7.248'],
    'splvmsaps120fv': ['10.41.7.249']
]

def gitCredentials = 'scm-incomm'
gitRepository = "https://github.com/InComm-Software-Development/vms-ms.git"
gitBranch = "develop"
repositoryId = 'maven-all'
groupId = 'com.incomm.vms'
artifactId =''
artifactExtension = 'jar'
artifactName = ''
emailDistribution = 'dstovall@incomm.com jrivett@incomm.com vhari@incomm.com ppiermarini@incomm.com'
deploymentLocation = "/opt/vms-ms/apps"
scriptDeploymentLocation = "/opt/vms-ms/scripts"
selectedServers = []
artifactVersion = 'null'
currentBuild.result = 'SUCCESS'

node('linux') {
    try {
        stage('Clean Workspace') {
            cleanWs()
        }

        stage('Singular Deploy') {
            if (singularDeploy == 'true'){
                
                servers = targets[targetEnv]

                echo "Servers in ${targetEnv} pool: ${servers}"

                selectedServers += inputRequested(servers)
                echo "Selected server : ${selectedServers}"

            }

            else {
                echo "singularDeploy was false... deploying to whole pool"
                selectedServers += targets[targetEnv]
            }
        }

        stage('Approval') {
            if ( targetEnv.matches(".*vmsaps.*|.*PROD.*|.*VMSAPS.*") ) {
                getApproval(approvalData)
            }
            else {
                echo "Deploying to ${targetEnv} doesn't require any approvals"
            }
        }

        stage('Select Service') { 
            dir('ms-repo') {
                githubCheckout(gitCredentials, gitRepository, gitBranch)
            }

            def my_choices = sh(script: 'cd ms-repo/ && ls -d vms* && cd ..', returnStdout: true).split()

            def wordlist = new ArrayList(Arrays.asList(my_choices))
    
            echo "list of service ${wordlist}"
            artifactId = input  message: 'select micro service from below list',ok : 'Deploy',id :'tag_id', 
            parameters:[choice(choices: wordlist, description: 'Select a microservice', name: 'artifactId')]
            echo "Selected service : ${artifactId}"
        }

        //select the artifact
        stage('Get Artifact') {
            echo "GETTING ARTIFACT: ${artifactId}"

            list = artifactResolverV2(repositoryId, groupId, artifactId, artifactExtension)
            echo "Available Versions for ${artifactId}: ${list}"
            artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
            parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
            sleep(3)
            artifactWget(repositoryId, groupId, artifactId, artifactExtension, artifactVersion)

            echo "WORKSPACE CONTENTS AFTER RESOLVING ARTIFACT: "
            sh "ls -thlar"
        }

        stage('Deployment') {
            echo "deploying to ${targetEnv}"
            deployComponents(targetEnv, selectedServers, "${artifactId}-${artifactVersion}.${artifactExtension}")
        }

    }
    catch (Exception e) {
        //stage('Notification'){
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.result = 'FAILURE'
    }
    finally {
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

// Runs deploy() for each target in targets[]
def deployComponents(String envName, targets, artFileName) {
    echo "my env= ${envName}"
    def stepsInParallel = targets.collectEntries {
        ["$it" : { deploy(it, artFileName) }]
    }
    parallel stepsInParallel
}

// Runs once for each iteration in deployComponents()
def deploy(def targetHostname, def artFileName) {
    echo "Deploying ${artifactId} to HOST: ${targetHostname}"
    echo "artifact File Name: ${artFileName}"
    echo "Deployment Location: ${deploymentLocation}"
    
    try {
        // Remove scripts and Copy new ones
        // sh """
        //     ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} 'mkdir -p ${scriptDeploymentLocation}'
        //     ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} 'rm -f ${scriptDeploymentLocation}/*'
        //     scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${env.WORKSPACE}/ms-repo/deployment/scripts/${targetEnv}/* root@${targetHostname}:${scriptDeploymentLocation}/
        //     ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} 'chmod +rx ${scriptDeploymentLocation}/*.sh'
        // """

        // Stop microservice
        sh """
            ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} "${scriptDeploymentLocation}/stop-${artifactId}.sh || echo 'service not started, so not stopping' && exit 0"
        """

        // Remove old artifact and copy over new one
        sh """
            ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} '/bin/rm -f ${deploymentLocation}/*${artifactId}*.${artifactExtension}'
            scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artFileName} root@${targetHostname}:${deploymentLocation}/
            ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} '/bin/chown -R vms-ms-user:vms-ms-user ${deploymentLocation}/${artFileName}'     
        """

        // Start microservice
        sh """
            ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} '${scriptDeploymentLocation}/start-${artifactId}.sh ${artFileName}'        
            ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} 'echo "${artFileName} service started"'        
        """

    } catch (exception) {
        echo exception
        echo "${artifactId}-${artifactVersion}.${artifactExtension} did NOT deploy properly. Please investigate"
        abortMessage = "${artifactId}-${artifactVersion}.${artifactExtension} did NOT deploy properly. Please investigate"
        currentBuild.result = 'FAILED'
    }
}
///// The End
