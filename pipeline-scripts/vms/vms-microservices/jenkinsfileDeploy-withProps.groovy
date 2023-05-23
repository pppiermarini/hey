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
gitBranch = "${VMS_MS_BRANCH}"
configBranch = "${VMS_CONFIG_BRANCH}"
singularDeploy = "${SINGULAR_DEPLOY}"
deployConfig = "${DEPLOY_CONFIG}"
deployJar = "${DEPLOY_JAR}"

// Globals
def gitCredentials = 'scm-incomm'
artifactId =''
credsName = "scm_deployment"
gitRepository = "https://github.com/InComm-Software-Development/vms-ms.git"
configRepository = "https://github.com/InComm-Software-Development/vms-config-data.git"
repositoryId = 'maven-all'
groupId = 'com.incomm.vms'
artifactExtension = 'jar'
artFileName = ''
emailDistribution = 'dstovall@incomm.com jrivett@incomm.com vhari@incomm.com ppiermarini@incomm.com'
deploymentLocation = "/opt/vms-ms/apps"
scriptDeploymentLocation = "/opt/vms-ms/scripts"
selectedServers = []
artifactVersion = 'null'
currentBuild.result = 'SUCCESS'
configDeploymentLocation = "/opt/vms-ms/vms-config-data"
def configFiles = []

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


node('linux') {
    try {
        stage('Clean Workspace') {
            cleanWs()
        }

        stage('checkouts') {
            dir('config-repo') {
                githubCheckout(gitCredentials, configRepository, configBranch)
            }
            dir('ms-repo') {
                githubCheckout(gitCredentials, gitRepository, gitBranch)
            }
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
            def my_choices = sh(script: 'cd config-repo/ && ls -d vms* && cd ..', returnStdout: true).split()
            def serviceList = new ArrayList(Arrays.asList(my_choices))
    
            echo "=============\nSELECT A SERVICE\n============="
            artifactId = singleInputRequested(serviceList)
            echo "Selected service : ${artifactId}"
        }

        if (deployConfig == 'true') {
            stage('get-configs') {
                configDeploymentLocation = configDeploymentLocation + "/" + artifactId
                configFiles += "config-repo/${artifactId}/${artifactId}.yml"
                configFiles += "config-repo/${artifactId}/${artifactId}-${targetEnv}.yml"
            }
        }

        if (deployJar == 'true') {
            //select the artifact
            stage('Get Artifact') {
                list = artifactResolverV2(repositoryId, groupId, artifactId, artifactExtension)
                echo "=============\nSELECT A VERSION\n============="
                artifactVersion = singleInputRequested(list)
                artifactWget(repositoryId, groupId, artifactId, artifactExtension, artifactVersion)

                artFileName = "${artifactId}-${artifactVersion}.${artifactExtension}"

                echo "WORKSPACE CONTENTS AFTER RESOLVING ARTIFACT: "
                sh "ls -thlar"
            }
        }

            stage('Deployment') {
                echo "deploying to ${targetEnv}"
                deployComponents(selectedServers, artFileName, configFiles)
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
def deployComponents(targets, artFileName, configFiles) {
    echo "targets: ${targets}"
    def stepsInParallel = targets.collectEntries {
        ["$it" : { deploy(it, artFileName, configFiles) }]
    }
    parallel stepsInParallel
}

// Runs once for each iteration in deployComponents()
def deploy(def targetHostname, def artFileName, def configFiles) {
    echo "Deploying ${artifactId} to HOST: ${targetHostname}"
    echo "artifact File Name: ${artFileName}"
    echo "Deployment Location: ${deploymentLocation}"
    sshagent(["${credsName}"]) {    
        try {
            if (deployJar == 'true') {
                // Stop microservice
                sh """
                    ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} "${scriptDeploymentLocation}/stop-${artifactId}.sh || echo 'service not started, so not stopping' && exit 0"
                """

                // Remove old artifact and copy over new one
                sh """
                    ssh -q -o StrictHostKeyChecking=no root@${targetHostname} '/bin/rm -f ${deploymentLocation}/*${artifactId}*.${artifactExtension}'
                    scp -q -o StrictHostKeyChecking=no ${artFileName} root@${targetHostname}:${deploymentLocation}/
                    ssh -q -o StrictHostKeyChecking=no root@${targetHostname} '/bin/chown -R vms-ms-user:vms-ms-user ${deploymentLocation}/${artFileName}'     
                """

                // Start microservice
                sh """
                    ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} '${scriptDeploymentLocation}/start-${artifactId}.sh ${artFileName}'        
                    ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${targetHostname} 'echo "${artFileName} service started"'        
                """
            }

            if (deployConfig == 'true') {
                // Copy config files
                sh """
                    ssh -q -o StrictHostKeyChecking=no root@${targetHostname} 'mkdir -p ${configDeploymentLocation}'
                """
                configFiles.each { thisConfigFile ->
                    sh """
                        scp -q -o StrictHostKeyChecking=no ${thisConfigFile} root@${targetHostname}:${configDeploymentLocation}
                    """
                }
                sh """
                    ssh -q -o StrictHostKeyChecking=no root@${targetHostname} '/bin/chown -R vms-ms-user:vms-ms-user ${configDeploymentLocation}'
                """
            }

        } catch (exception) {
            echo exception
            echo "${artifactId}-${artifactVersion}.${artifactExtension} did NOT deploy properly. Please investigate"
            abortMessage = "${artifactId}-${artifactVersion}.${artifactExtension} did NOT deploy properly. Please investigate"
            currentBuild.result = 'FAILED'
        }
    }
}
///// The End