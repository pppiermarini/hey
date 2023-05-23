import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

credsName = "scm_deployment"
userInput="Promote"
//targetEnv="${targetEnv}"
//targetEnv="${target_Env}"  'qa-edge': ['10.42.83.143', '10.42.83.147']
testName="myTest"

targets = [
    'dev':  ['10.42.20.47', '10.42.20.48'],
    'qa': ['10.42.83.79', '10.42.83.128'],
    'uat': ['10.42.50.9','10.42.49.37'],
    'load': ['10.42.5.157','10.42.2.246','10.42.5.155']
]

approvalData = [
	'operators': "[anandavaram,pabbavaram,sthulaseedharan,kkoya]",
	'adUserOrGroup' : 'anandavaram,pabbavaram,sthulaseedharan,kkoya',
	'target_env' : "${targetEnv}"
]

emailDistribution="sthulaseedharan@incomm.com kkoya@incomm.com pabbavaram@incomm.com rkale@incomm.com vhari@incomm.com anandavaram@incomm.com ppiermarini@incomm.com"
//emailDistribution="ppiermarini@incomm.com"
//Get from DEV
artifactDeploymentLoc ="/app/ctps-ogc-custsvcs"

serviceName="ctps-ogc-custsvcs.service"
pipeline_id="${env.BUILD_TAG}"

user="ogc"
group="ogc"
chmod="a+x"


/*
gitRepository="https://github.com/InComm-Software-Development/ctps-ordergiftcard-ordermanagement.git"
gitTag="${Branch}"
gitCreds="scm-incomm"
*/

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"



//Artifact Resolver	input specifics, TBD
repoId = "maven-release"
groupId = "com.incomm.ordergiftcard"
artifactId = "ctps-ogc-custsvcs"
env_propertyName = "ART_VERSION"
artExtension = "jar"
artifactName = "ctps-ogc-custsvcs.jar"

//globals
approveResult="null"
artifactVersion="${artifactVersion}"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

currentBuild.result = 'SUCCESS'


node('linux'){
	try { 
		cleanWs()	

		stage('Approval Check') {
        	if ((targetEnv == 'pre-prod') || (targetEnv == 'prod') || (targetEnv == 'uat-edge') || (targetEnv == 'load')) {
        		approveResult = getApproval(approvalData)
				echo "Approval Result: ${approveResult}"
        	}
        }
		
		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote'){
				//githubCheckout(gitCreds,gitRepository,gitTag)
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

				echo "${artifactId}-${artifactVersion}.${artExtension}"
				sh "ls -ltr"
			
			} else {
				echo "not getting artifact during a release build"
			}
			
		}
		
		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			
			} else {
				echo "not deploying during a release build"
			}

		}

		
	} catch (exc) {

		currentBuild.result = 'FAILURE'
		echo 'ERROR:  '+ exc.toString()
		throw exc
		
	} finally {
	echo "${currentBuild.currentResult}"
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	//<li>ServerNames: ${targetEnv}= ${Servers}</li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetEnv}</li>
					<li>Deployed Service Deployed: ${serviceName}</li>
					<li>Deployed Artifact Version: ${artifactVersion}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if (currentBuild.currentResult == "ABORTED"){
		echo "if aborted"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetEnv}</li>
					<li>Deployed Service Deployed: ${serviceName}</li>
					<li>Deployed Artifact Version: ${artifactVersion}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.currentResult == "SUCCESS"){
		echo "success"
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetEnv}</li>
					<li>Deployed Service Deployed: ${serviceName}</li>
					<li>Deployed Artifact Version: ${artifactVersion}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""				
	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
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
	sshagent([credsName]){
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl stop ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv ${artifactDeploymentLoc}/${artifactId}.${artExtension}'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLoc}/${artifactId}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactId}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl start ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl status ${serviceName}'
		"""
	}

}




///// The End