import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*
import java.util.regex.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
gitBranch = "${GIT_BRANCH}"
targetHost = "${TARGET_HOST}"
targetEnv = 'dev' // Should normally be mapped to UI var

// Globals
emailDistribution = "jrivett@incomm.com"
gitRepository = 'https://github.com/InComm-Software-Development/ctps-coreaps-pipelines.git'
gitCreds = 'scm-incomm'
repoId = 'maven-all'
ymlFileName = 'bes-int-pipeline.yml'
stackName = 'bes-int'
driveLetter = 'D'
xmlSources = []
xmlDestinations = []
serviceName = 'JBOSS-EAP-7.3-BESINT'

node('windows'){
    try {  

        stage('checkout'){
            cleanWs()
            githubCheckout(gitCreds,gitRepository,gitBranch)
        }

        stage('get-xml-paths'){
            // Need to do it this way in order to pass stdout to a groovy variable
            cmd = """@echo off && powershell -Command \"Get-ChildItem -Path ${env.WORKSPACE} -Recurse -Name *.xml | Out-String\" """
            xmls = bat(returnStdout: true, script: cmd)
            xmlPaths = xmls.trim().split("\n")

        }

        stage('prepend-drive-letters-to-xml-paths'){
            xmlPaths.each { path ->
                // Clean carriage returns and newlines from paths and doubleup backslashes because windows sucks
                newPath = path.replace("\\", "\\\\")
                newPath = newPath.replaceAll("\\n", "");
                newPath = newPath.replaceAll("\\r", "");
                xmlSources += newPath

                // Create destination paths with drive letter
                driveAndDollar = driveLetter + '\\$'
                destination = newPath.replaceAll("bes-int-dev", driveAndDollar)
                xmlDestinations += destination
            }
            echo "xmlSources: ${xmlSources}"
            echo "xmlDestinations: ${xmlDestinations}"
        }

        stage('read-yml'){
            yml = readyml()
        }

        stage('get-artifacts'){
            // TODO: parallelize this
            if ("${DEPLOY_ARTS}" == 'true') {
                echo "Downloading artifacts from artifactory..."

                yml.deployments.each{ subStack, properties -> 
                    echo "subStack: ${subStack}"
                    echo "${subStack}.location: ${properties.location}"
                    echo "${subStack}.artifacts: ${properties.artifacts}"

                    properties.artifacts.each{ art, artinfo ->
                        stringVersion = artinfo.version.toString()
                        filename = "${art}-${stringVersion}.${artinfo.extension}"
                        echo "Downloading artifact: ${filename}"
                        artifactWgetWindows(repoId, artinfo.groupid, art, artinfo.extension, stringVersion)
                    }
                    echo "workspace dir after getting artifacts in '${subStack}':"
                    bat "powershell ls"
                }
            }
        }

        stage('Stop-Service') {
            if ("${DEPLOY_ARTS}" == 'true') {
                stopService(targetHost)
            }
        }

        stage('deploy-artifacts'){
            if ("${DEPLOY_ARTS}" == 'true') {
                echo "Deploying artifacts to target host: ${targetHost}"

                yml.deployments.each{ subStack, properties -> 
                    echo "subStack: ${subStack}"
                    echo "${subStack}.location: ${properties.location}"
                    echo "${subStack}.artifacts: ${properties.artifacts}"

                    properties.artifacts.each{ art, artinfo ->
                        stringVersion = artinfo.version.toString()
                        filename = "${art}-${stringVersion}.${artinfo.extension}"

                        echo "DEPLOYING ${filename} TO \\\\${targetHost}\\${properties.location}\\"
                        deployArtifact("${targetHost}", "${properties.location}", filename)
                    }
                }
            }
        }

        stage('Copy-XMLs') {
            // We're iterating two lists at once so traditional for loop is necessary
            limit = xmlSources.size()
            for (int i = 0; i < limit; i++) {
                deployXML(targetHost, xmlSources[i], xmlDestinations[i])
            }
        }

        stage('Start-Service') {
            if ("${DEPLOY_ARTS}" == 'true') {
                startService(targetHost)
            }
        }
    }

    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailv3(emailDistribution, getBuildUserv1())	
	}
}

def deployArtifact(target_hostname, artifactDeploymentLoc, artFileName) {
    echo "Copying artifact: ${artFileName}"
    echo " the target is: ${target_hostname}"
    echo "Deployment Location is: ${artifactDeploymentLoc}"

    bat """    
        dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
        del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\${artFileName}
        sleep(4)
        echo "deploying ${artFileName}"
        copy /Y ${artFileName} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
    """
}

def deployXML(target_hostname, xmlSourcePath, xmlDeploymentPath) {
    xmlFileName = xmlSourcePath.split("\\\\").last()

    // Clean backslashes inputs because windows sucks
    xmlSourcePath = xmlSourcePath.replace("\\\\", "\\")
    xmlDeploymentPath = xmlDeploymentPath.replaceAll("${xmlFileName}", "")
    xmlDeploymentPath = xmlDeploymentPath.replace("\\\\", "\\")
    xmlDeploymentPath = xmlDeploymentPath.substring(0, xmlDeploymentPath.size() - 1);

    echo "xmlFileName: ${xmlFileName}"
    echo "xmlSourcePath: ${xmlSourcePath}"
    echo "xmlDeploymentPath: ${xmlDeploymentPath}"

    bat """
        dir \\\\${target_hostname}\\${xmlDeploymentPath}
        del /F /Q \\\\${target_hostname}\\${xmlDeploymentPath}\\${xmlFileName}
        copy /Y ${env.WORKSPACE}\\${xmlSourcePath} \\\\${target_hostname}\\${xmlDeploymentPath}\\
        dir \\\\${target_hostname}\\${xmlDeploymentPath}
    """
}

def stopService(target_hostname) {
    echo "Stopping Service ${serviceName} on HOST: ${target_hostname}"
    bat """
        powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Stop-Service
    """
}

def startService(target_hostname) {
    echo "Starting Service ${serviceName} on HOST: ${target_hostname}"
    bat """
        powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
    """
}

def readyml() {
    echo "Reading ${ymlFileName}"
    
    yml = readYaml (file: "${stackName}-${targetEnv}/${ymlFileName}")
    if (yml == null) {
        throw new Exception("${ymlFileName} not found in the project files.")
    }
    return yml
}