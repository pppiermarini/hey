import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


emailDistribution="rkale@incomm.com vhari@incomm.com vpilli@incomm.com mpalve@incomm.com nkassetty@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

maven="/opt/apache-maven-3.9.9/bin/mvn"

//	imageLabel = "${Image_label}"
//currentBuild.currentResult="SUCCESS"

imageName = "red-db-services"

imageDeploymentLoc="/app/docker/tmp"

Deploy_Config="${Config}"

configLoc="/app/install/redisdbsvc"
gitLoc="docker"

gitYmlInstallFile="redis-db-svc-docker-compose.yml"

commandFile="command.sh"

targetenvs="${targetEnv}"

gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-redisdbservices.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

//https://github.com/InComm-Software-Development/ctps-dbservices-project-initdbservices.git

//@TODO: Confirm with Team -> Taken from: https://wiki.incomm.com/pages/viewpage.action?pageId=141008525 (Docker Application section)
targets = [
    'dev':  ['10.42.20.20'],
    'qa': ['10.42.82.239'],
    'uat':['10.42.50.200'],
    'load': ['10.42.5.203'],
    'pre-prod':['10.40.7.214'],
    'prod':['10.40.7.231']

]

def allSnaps=[]
userInput="Promote"
imageLabel=''
def Deployment_approval=''

approvalData = [
	'operators': "[nkassetty,vpilli,vhari,mpalve]",
	'adUserOrGroup' : 'nkassetty,vpilli,vhari,mpalve',
	'target_env' : "${targetEnv}"
]

node('docker'){
	try { 
			
		cleanWs()
		//select the artifact 
		/*stage('Docker-Pull'){		

			echo "Attempting to get from Artifactory"
			docker.withRegistry('https://docker.maven.incomm.com/', 'svc_docker_ro') {
        		def myImage = docker.image("${imageName}:${imageLabel}")
        		myImage.pull()
        		sh "docker save -o ${imageName}-${imageLabel}.tar docker.maven.incomm.com/${imageName}"

    		}

		}
		stage('Call to Docker'){

			final String call = "https://docker.maven.incomm.com/artifactory/api/docker/docker-local/v2/${imageName}/tags/list"
	
	withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {

			response = sh(script: "curl -X GET ${call} -u ${USER}:${PASS}", returnStdout: true).trim()
			def dataApi = readJSON text: response
			
			for (i in dataApi.tags) {
				allSnaps.add(i)
			}
		}
	}


	stage('Choose a tag') {
		try {
			timeout(time: 90, unit: 'SECONDS'){
                    imageLabel = input  message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
                    parameters:[choice(choices: allSnaps, description: 'Select a tag for this build', name: 'TAG')]
                	}

    				echo "${imageLabel}"
		}
		catch (err) {
			echo "Build Aborted"
			currentBuild.currentResult = "ABORTED"
		}	

	}*/

		stage('Approval Check') {
        	if ((targetEnv == 'pre-prod') || (targetEnv == 'prod') || (targetEnv == 'qa') || (targetEnv == 'uat')) {
        		getApproval(approvalData)
        	}
        }

		stage("Deploy to ${targetenvs}") {
			githubCheckout(gitCreds,gitRepository,gitBranch)

			deployComponents(targetenvs, targets[targetenvs], Deploy_Config)

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
					<li>Deployed Environment: ${targetenvs}</li>
					<li>Docker Image Name: ${imageName}</li>
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
		echo "Generating the directory"
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
		"""
		echo "Deploying the image"
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
