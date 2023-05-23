import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


emailDistribution="rkale@incomm.com vhari@incomm.com vpilli@incomm.com mpalve@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

maven="/opt/apache-maven-3.9.9/bin/mvn"

//	imageLabel = "${Image_label}"
//currentBuild.currentResult="SUCCESS"

imageName = "spl-services-google"

imageDeploymentLoc="/app/docker/tmp"

Deploy_Config="${Config}"

configLoc="/app/install/google"
gitLoc="docker"

gitYmlInstallFile="spil-google-svc-docker-compose.yml"

commandFile="command.sh"

targetenvs="${targetEnv}"

gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-google.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

//https://github.com/InComm-Software-Development/ctps-dbservices-project-initdbservices.git

//@TODO: Confirm with Team -> Taken from: https://wiki.incomm.com/pages/viewpage.action?pageId=141008525 (Docker Application section)
targets = [
    'dev':  ['10.42.20.20'],
    'qa': ['10.42.82.239'],
    'uat':['10.42.50.200'],
    'pre-prod':['10.40.7.214'],
    'load':['10.42.5.203'],
    'prod':['10.40.7.231']

]

approvalData = [
	'operators': "[ppiermarini,vpilli,vhari,mpalve]",
	'adUserOrGroup' : 'ppiermarini,vpilli,vhari,mpalve',
	'target_env' : "${targetEnv}"
]


def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''

node('docker'){
	try { 
			
		cleanWs()
		stage('Approval Check') {
        	if ((targetEnv == 'pre-prod') || (targetEnv == 'prod') || (targetEnv == 'qa') || (targetEnv == 'uat')) {
        		getApproval(approvalData)
        	}
        }

		stage("Deploy to ${targetenvs}") {
			githubCheckout(gitCreds,gitRepository,gitBranch)

			deployComponents(targetenvs, targets[targetenvs], Deploy_Config)

		}
		
		
		stage("DevTest Run") {
        	if ((targetEnv == 'qa') || (runDevTest == 'true')) {

				sh "curl -o ${SuiteName}.txt http://10.42.97.79:1505/lisa-invoke/runSuite?suitePath=/home/lisa/DevTest10.6/DevTest/Projects/JenkinsISO/Tests/Suites/${SuiteName}.ste"
				output = readFile "${SuiteName}.txt"

				if(output.contains("<status>OK")){
					echo " LISA test status contains OK"
					def pattern = output =~ /<pass count=(.+?)\/>/ //The Pattern
					def passCount = pattern[0][1].replaceAll("\"", "") //Removes quotes
					echo "Pass Count = ${passCount}"
				}else if ((!output.contains("<fail count=\"0\"")) || (!output.contains("<error count=\"0\""))){
				
					def pattern1 = output =~ /<fail count=(.+?)\/>/ //The Pattern
					def failCount = pattern1[0][1].replaceAll("\"", "") //Removes quotes
					echo "Fail Count = ${failCount}"
					
					def pattern2 = output =~ /<error count=(.+?)\/>/ //The Pattern
					def errorCount = pattern2[0][1].replaceAll("\"", "") //Removes quotes
					echo "Error Count = ${errorCount}"
					
					throw new AbortException(" aborted.")
					currentBuild.result='FAILED'
				} else {
					echo "LISA test case invocation failed"
					error "LISA test case invocation failed"
						throw new AbortException(" aborted.")
						currentBuild.result='FAILED'
				}

			} else {
				
				echo "runDevTest= false"
				
			}
		}
	
		
	} catch(exc) {
			currentBuild.currentResult="FAILED"
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
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
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
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if((currentBuild.currentResult == "SUCCESS")&&(runDevTest == 'true')){
		echo "success"
	Servers = targets.get(targetEnv)
    emailext attachmentsPattern: '${SuiteName}.txt', mimeType: 'text/html', attachLog: true, 
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
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""				
	}else{
		
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
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
					<li>Docker Image Version: ${imageLabel}</li>
					<li>ServerNames: ${Servers}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""	
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node


def deployComponents(envName, targets, Deploy_Config){
	
	echo "my env= ${envName}"
	echo "my target= ${targets}"
	echo "Deploy config: " + Deploy_Config
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName, Deploy_Config) } ]
	}

	parallel stepsInParallel

	
}

def deploy(target_hostname, envName, Deploy_Config) {
	echo " the target is: ${target_hostname}"
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker import ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	//		scp -q -o StrictHostKeyChecking=no ${imageName}-${imageLabel}.tar root@${target_hostname}:${imageDeploymentLoc}/
	//	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	//	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -f ${imageDeploymentLoc}/${imageName}*.tar'

	sshagent([credsName]) {
		echo "Deploying the image"
		echo "Generating the directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
		"""
		echo "deployconfig set to : " + Deploy_Config
		if (Deploy_Config == 'Y'){
			sh """
				scp -q -o StrictHostKeyChecking=no ${gitLoc}/${envName}/${gitYmlInstallFile} root@${target_hostname}:${configLoc}
			"""
		}
	    sh"""
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${envName}/${commandFile} root@${target_hostname}:${configLoc}
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/sh ${configLoc}/${commandFile}'

		"""
		}
}



///// The End
