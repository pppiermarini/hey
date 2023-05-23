import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

targets = [
    'dev':  ['10.42.17.64', '10.42.17.65', '10.42.17.67'],
    'qa':   ['10.42.80.26','10.42.80.27'],
    'int1': ['10.42.32.205'],
    'int2': ['10.42.32.206'],
    'int': ['10.42.32.205','10.42.32.206']

]
// JENKINS UI PARAMS
gitTag="${TAG_CONFIGURATION}"
pipeline_id="${env.BUILD_TAG}"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

//globals
credsName = "scm_deployment"
userInput="Promote"
testName="myTest"
emailDistribution="atiruveedhi@incomm.com glodha@incomm.com rkale@incomm.com vhari@incomm.com"
artifactDeploymentLoc ="/var/opt/pivotal/pivotal-tc-server-standard/mdm-workflow-instance/webapps"
instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/mdm-workflow-instance/logs"
serviceName="mdm-workflow-instance"
user="tcserver"
group="pivotal"
chmod="755"
configFolderLoc="/var/opt/pivotal/pivotal-tc-server-standard/mdm-workflow-instance/lib/"
gitRepository="https://github.com/InComm-Software-Development/mdm-product-setup-configuration.git"
gitCreds="scm-incomm"
repoId = 'maven-all'
groupId = 'com.incomm.mdm'
artifactId = 'workflow'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'workflow.war'
warFolderName = "workflow"
relVersion="null"
currentBuild.result = 'SUCCESS'

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage('Get Artifact'){
			githubCheckout(gitCreds,gitRepository,gitTag)

			selectArtifact()
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
			echo "BEFORE RENAME: "
			sh "ls -tlar *.${artExtension}" 

			sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"

			echo "AFTER RENAME: "
			sh "ls -tlar *.${artExtension}" 
			
		}

		stage("Deployment to ${targetEnv}"){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}")

		}

		
	} catch (exc) {
		currentBuild.result = 'FAILURE'
		echo 'ERROR:  '+ exc.toString()
		throw exc
	} finally {
		if (currentBuild.result == "FAILURE"){
			echo "if failure"
			emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER}  - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"
		}else if(currentBuild.result == "SUCCESS"){
			echo "if success"
			emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER}  - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"
		}else{		
			echo "LAST"
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'"""
		sleep(10)

		sh """	
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${warFolderName}*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
		scp -o StrictHostKeyChecking=no -r ${envName}/*.properties root@${target_hostname}:${configFolderLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${configFolderLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${configFolderLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}

}

def selectArtifact() {
	// Select and download artifact
	list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
	echo "the list contents ${list}"
	artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
	parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
	sleep(3)
}
///// The End