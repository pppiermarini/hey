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
    'int1': ['10.42.32.205'],
    'int2': ['10.42.32.206'],
    'intg': ['10.42.32.205','10.42.32.206'],
    'qa':   ['10.42.80.26','10.42.80.27'],
    'uat': ['10.42.48.149','10.42.49.201'],
	'prod': ['spmdmapp05v']

]

// Server information for reference
// spmdmapp05v 10.40.5.117
//stmdmapp01v0771v 10.42.32.205


approvalData = [
	'operators': "[glodha,kroopchand,ppiermarini]",
	'adUserOrGroup' : 'glodha,kroopchand,ppiermarini',
	'target_env' : "${targetEnv}"
]

// JENKINS UI PARAMS
gitTag="${TAG_CONFIGURATION}"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

//globals
credsName = "scm_deployment"
emailDistribution="atiruveedhi@incomm.com glodha@incomm.com"
artifactDeploymentLoc ="/var/opt/pivotal/pivotal-tc-server-standard/mdm-housekeeper-instance/webapps"
serviceName="mdm-housekeeper-instance"
user="tcserver"
group="pivotal"
chmod="755"
configFolderLoc="/var/opt/pivotal/pivotal-tc-server-standard/mdm-housekeeper-instance/lib/"
gitRepository="https://github.com/InComm-Software-Development/mdm-house-keeper.git"
gitCreds="scm-incomm"
gitFolder="configuration"
repoId = 'maven-all'
groupId = 'com.incomm.mdm'
artifactId = 'house-keeper-webapp-ui'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'house-keeper-webapp-ui.war'
warFolderName = 'house-keeper-webapp-ui'
artifactVersion="null"
currentBuild.result = 'SUCCESS'

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage ('Get Artifact'){
			// download configs
			githubCheckout(gitCreds,gitRepository,gitTag)

    	     // Select and download artifact
            list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
            echo "the list contents ${list}"
            artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
            parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
            sleep(3)
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)

			// rename artifact
            echo "BEFORE RENAME: "
			sh "ls -tlar *.${artExtension}" 
			sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactId}.${artExtension}"
			echo "AFTER RENAME: "
			sh "ls -tlar *.${artExtension}" 
    	}
		
		stage('Approval Check') {
        	if (targetEnv == 'prod') {
        		approveResult = getApproval(approvalData)
				echo "Approval Result: ${approveResult}"
        	}
        }

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (targetEnv != 'prod'){

				deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			
			} else {
			
				echo " Deploying to PROD "
				deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
				
			}

		}
		
		
	} catch (exc) {
		currentBuild.result = 'FAILURE'
		echo 'ERROR:  '+ exc.toString()
		throw exc
	} finally {
		if (currentBuild.result == "FAILURE"){
			echo "if failure"
			emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

		}else if(currentBuild.result == "SUCCESS"){
			echo "if success"
			emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"
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
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${warFolderName}*'
			scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
		"""
		if (("${envName}" == "int2") || ("${envName}" == "int1")) { 
			envName = "intg"
			sh """	
				scp -o StrictHostKeyChecking=no -r ${gitFolder}/${envName}/* root@${target_hostname}:${configFolderLoc}
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
		else {
			sh """
				scp -o StrictHostKeyChecking=no -r ${gitFolder}/${envName}/* root@${target_hostname}:${configFolderLoc}
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

}
///// The End