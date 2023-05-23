import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


//Add the email distribution 
emailDistribution=""

//General pipeline 
credsName = 'scm_deployment'


//Setting the targetEnv choice param from Jenkins config
targetenvs="${targetEnv}"

//ImageLabel a Jenkins string config to add 
imageLabel = "${Image_label}"

//The docker registry image name pushed to maven. 
imageName = ""

//The server location to scp to the image
imageDeploymentLoc=""


//Github repo. details
gitRepository=""
gitBranch=""
gitCreds="scm-incomm"

//Our target boxes per env 
targets = [
    'dev':  []

]


//On the Jenkins docker note
node('docker'){
	try { 
		
		cleanWs()

		stage('Docker-Pull'){
			githubCheckout(gitCreds,gitRepository,gitBranch)
			echo "Attempting to get from Artifactory"
			//Docker registry connection to pull the image and save onto the Jenkins
			docker.withRegistry('https://docker.maven.incomm.com/', 'svc_docker_ro') {
        		def myImage = docker.image("${imageName}:${imageLabel}")
        		myImage.pull()
        		sh "docker save -o ${imageName}-${imageLabel}.tar docker.maven.incomm.com/${imageName}:${imageLabel}"
    		}

		}

		stage("Deploy to ${targetenvs}") {
			echo "{$targetenvs}"
			deployComponents(targetenvs, targets[targetenvs], Deploy_Config)

		}

		
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
	}
}

} //end of node


def deployComponents(envName, targets, Deploy_Config){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName, Deploy_Config) } ]
	}
	parallel stepsInParallel


	
}


//Basic commands for deploying the container and loading it on the the target box.
def deploy(target_hostname, envName, Deploy_Config) {
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		echo "Deploying the image"
		sh """
		scp -q -o StrictHostKeyChecking=no ${imageName}-${imageLabel}.tar root@${target_hostname}:${imageDeploymentLoc}/
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -f ${imageDeploymentLoc}/${imageName}*.tar'
		"""
	}

}



///// The End
