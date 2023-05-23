import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _



//emailDistribution="rkale@incomm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.3.9/bin/mvn"

isHostEmpty = "${HOST}"

targetenvs="${HOST}".split(',')

operations="${OPERATIONS}"

docker_version= "${DOCKER_VERSION}"

targets = []

credsName = "scm_deployment"

node('linux'){
	try { 
		cleanWs()
		//echo "the targetenvs is ${targetenvs}"
		stage('testing logic') {
		if(isHostEmpty != null && !isHostEmpty.isEmpty()) {
		for (i in targetenvs) {
			targets.add(i.trim())
		}
	} 

	else {
    	currentBuild.result = 'FAILURE'
		error('Build aborted because supplied target list is empty')
 
	}

			} //stage

		stage('Setting info') {
			setInfo()
			outputCheck()
		}

		stage('targets') {
		 upgradeDockerVersion(targets) }
		} //try 
		
	catch (any) {
		echo any
		echo "Muy Mal"
	} finally {
	
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
		//emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.currentResult == "SUCCESS"){
		echo "if success"
		//emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
		}
	}


		
}  //end of node

def upgradeDockerVersion(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployupgradeDockerVersion(it) } ]
	}
	parallel stepsInParallel
	
}


def deployupgradeDockerVersion(target_hostname) {
	echo "${target_hostname}, ${operations}, ${docker_version}"
}

def setInfo(){

	if(docker_version == null || docker_version.isEmpty()) {
		echo "Docker version is empty, setting to latest supported v20.10.5"
		docker_version = "20.10.5"
	}
	echo "Adding docker-ce version"
	docker_ce_choice = "docker-ce-${docker_version}"

	echo "Adding containerd IO"

	containerd_choice = "containerd.io"

	echo "Adding Docker CE Cli"

	docker_ce_cli_choice = "docker-ce-cli-${docker_version}"


}

def outputCheck() {
	echo "${containerd_choice}"
	echo "${docker_ce_choice}"
	echo "${docker_ce_cli_choice}"

}
///// The End
