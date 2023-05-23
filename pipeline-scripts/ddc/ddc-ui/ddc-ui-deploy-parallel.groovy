import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
targetEnv = "${TARGET_ENV}"
deployingJar = "${DEPLOY_JAR}"
deployingStatic = "${DEPLOY_STATIC}"
singularDeploy = "${SINGULAR_DEPLOY}"

// Globals
emailDistribution = "vhari@incomm.com, dstovall@incomm.com, ppiermarini@incomm.com, jrivett@incomm.com"
credsName = "scm_deployment"
groupId = "com.incomm.web"
selectedServers = []
def serviceName = "ddc-ui"

jarArtifactId = "ddc-ui"
jarArtExtension = "jar"
jarDeploymentLocation = "/var/opt/ddc-ui"
jarFileName = "${jarArtifactId}.${jarArtExtension}"

staticArtifactId = "ddc-static"
staticArtExtension = "tar"
staticDeploymentLocation = "/var/www/ddc-static"

approvalData = [
    'operators': "[pchattaraj,vchavva,rmohl,amohammad,dbelardo,DBELARDO,kedupuganti,nsamineni,smohammad,vganjgavkar]",
    'adUserOrGroup' : 'pchattaraj,vchavva,rmohl,amohammad,dbelardo,DBELARDO,kedupuganti,nsamineni,smohammad,vganjgavkar',
]

// HOSTS
targets = [
    'dev': ['10.42.17.80', '10.42.17.81'],
    'qa': ['10.42.81.150', '10.42.81.151'],
    'uat': ['10.42.48.125', '10.42.48.126'],
    'pre-prod': ['10.40.98.61'],
    'prod': ['10.40.98.250', '10.40.98.251', '10.40.98.253']
]


node('linux2'){
    try {  
        stage('Clean-Workspace') {
            cleanWs()
        }

        if ( targetEnv.matches(".*prod.*|.*PROD.*") || targetEnv.matches(".*uat.*|.*UAT.*") ) {
            getApproval(approvalData)
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

        stage('Get-Artifacts') {
            if (deployingJar == 'true') {
                // LET USER SELECT A JAR VERSION AND DOWNLOAD IT
                jarVersions = artifactResolverV2('repo', groupId, jarArtifactId, jarArtExtension)
                echo "==================================="
                echo "       SELECT A JAR VERSION"
                echo "==================================="
                selectedJarVersion = singleInputRequested(jarVersions)
                artifactWget('repo', groupId, jarArtifactId, jarArtExtension, "${selectedJarVersion}")
                sh """ mv ${jarArtifactId}-${selectedJarVersion}.${jarArtExtension} ${jarFileName} """
            }

            if (deployingStatic == 'true') {
                // LET USER SELECT A STATIC VERSION THEN DOWNLOAD IT
                staticVersions = artifactResolverV2('repo', groupId, staticArtifactId, staticArtExtension)
                echo "=============================================="
                echo "      SELECT A STATIC COMPONENT VERSION"
                echo "=============================================="
                selectedStaticVersion = singleInputRequested(staticVersions)
                artifactWget('repo', groupId, staticArtifactId, staticArtExtension, "${selectedStaticVersion}")
                staticFilename = "${staticArtifactId}-${selectedStaticVersion}.${staticArtExtension}"
            }

            echo "WORKSPACE DIR AFTER GETTING ARTIFACTS:"
            sh "ls -tlahr"
        }

        stage('Stop-Service') {
            if (deployingJar == 'true') {
                stopServiceInParallel(selectedServers, serviceName)
            }
        }

        stage('Deploy-Files') {
            if (deployingJar == 'true') {
                copyFileInParallel(selectedServers, jarFileName, jarDeploymentLocation)
            }

            if (deployingStatic == 'true') {
                cleanDirInParallel(selectedServers, staticDeploymentLocation)
                copyFileInParallel(selectedServers, "ddc-static-${selectedStaticVersion}.tar", staticDeploymentLocation)
                untarInParallel(selectedServers, staticDeploymentLocation, staticFilename)
            }
        }

        stage('Start-Service') {
            if (deployingJar == 'true') {
                startServiceInParallel(selectedServers, serviceName)
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

def stopService(targetHostname, serviceName) {
    sshagent([credsName]) {
        sh """
            ssh -o StrictHostKeyChecking=no root@${targetHostname} 'service ${serviceName} stop'
        """
    }
}

def startService(targetHostname, serviceName_) {
    sshagent([credsName]) {
        sh """
            ssh -o StrictHostKeyChecking=no root@${targetHostname} 'service ${serviceName_} start'
        """
    }
}

def cleanDir(targetHostname, pathToClean) {
    sshagent([credsName]) {
        sh """
            ssh -q -o StrictHostKeyChecking=no root@${targetHostname} 'rm -rf ${pathToClean}/*'
        """ 
    }
}

def copyFile(targetHostname, fileName, deploymentPath) {
    sshagent([credsName]) {
        sh """
            ssh -q -o StrictHostKeyChecking=no root@${targetHostname} 'if [ -f "${deploymentPath}/${fileName}" ]; then rm "${deploymentPath}/${fileName}"; fi'
            scp -q -o StrictHostKeyChecking=no -r ${env.WORKSPACE}/${fileName} root@${targetHostname}:${deploymentPath}/${fileName}
        """ 
    }
}

def untar(targetHostname, filePath, fileName) {
    sshagent([credsName]) {
        sh """
            ssh -q -o StrictHostKeyChecking=no root@${targetHostname} 'tar -xf ${filePath}/${fileName} -C ${filePath}'
        """
    }   
}

def stopServiceInParallel(hosts, serviceName) {
    // STOP THE SERVICE IN PARALLEL
    def stepsInParallel =  hosts.collectEntries {
        [ "$it" : { stopService(it, serviceName) } ]
    }
    parallel stepsInParallel
}

def startServiceInParallel(hosts, serviceName) {
    // start THE SERVICE IN PARALLEL
    def stepsInParallel =  hosts.collectEntries {
        [ "$it" : { startService(it, serviceName) } ]
    }
    parallel stepsInParallel
}

def cleanDirInParallel(hosts, pathToClean) {
    // COPY FILE IN PARALLEL
    def stepsInParallel =  hosts.collectEntries {
        [ "$it" : { cleanDir(it, pathToClean) } ]
    }
    parallel stepsInParallel
}

def copyFileInParallel(hosts, fileName, deploymentPath) {
    // COPY FILE IN PARALLEL
    def stepsInParallel =  hosts.collectEntries {
        [ "$it" : { copyFile(it, fileName, deploymentPath) } ]
    }
    parallel stepsInParallel
}

def untarInParallel(hosts, filePath, fileName) {
    def stepsInParallel =  hosts.collectEntries {
        [ "$it" : { untar(it, filePath, fileName) } ]
    }
    parallel stepsInParallel
}