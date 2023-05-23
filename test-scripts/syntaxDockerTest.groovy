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

targetenvs="${targetEnv}"

containerd_io = []
docker_ce = []
docker_ce_cli = []
docker_ce_rootless = []
docker_ce_selinux = []


docker_ce_choice = ""
containerd_choice =""
docker_ce_cli_choice =""
//deploy_config_application="${Config_Application}"


targetsEppcon = [
 'dev':  ['v1']
 ]


targetRtgepp = [
 'dev':  ['v2']
 ]

node('linux'){
	try { 
		cleanWs()

		stage('testing logic') {
			getInfo()
			outputCheck()
			} //stage

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

def getInfo(){
			if(operation == "Upgrade") {
			final String call = "https://docker.maven.incomm.com/artifactory/dockerinstall/7/x86_64/stable/Packages/"
			withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
			response = sh(script: """curl -X GET ${call} -u ${USER}:${PASS}""", returnStdout: true).trim()

			//echo response
			response = response.toString()


			String stripped_initial = response.replaceAll("\\<[^>]*>", "")

			//echo stripped_initial

			//def filter = stripped_initial.replaceAll("\\->.+","")
			
			//def packages = stripped_initial.replaceAll("(?m)^.(?<!rpm)+(\n|\$)","")
			def packages = stripped_initial.replaceAll(".rpm.*","")
			//echo packages
			def trim_packages = packages.trim()	



			writeFile(file: 'packages.txt', text: trim_packages)
	        sh "ls -l"
	        def all_packages = readFile('packages.txt')

	        def lines = all_packages.readLines()
			
			for (i in lines) {
				//i = i - ".rpm"
				if (i.contains("containerd.io")) {
					containerd_io.add(i)
				} 
				//\bswlang/([a-zA-Z0-9_]{2})/?
				///Total.*?(\d\S*)/
				if (i.matches("docker-ce-([0-9].+)")) {
					//echo i
					docker_ce.add(i)
				}
				if (i.contains("docker-ce-cli")) {
					docker_ce_cli.add(i)
				}
				if (i.contains("docker-ce-rootless-extras")) {
					docker_ce_rootless.add(i)
				}
				if (i.contains("docker-ce-selinux")) {
					docker_ce_selinux.add(i)
				}
			}

			echo "Choose a Docker CE"
			docker_ce_choice = input  message: 'Select the version of Docker CE',ok : 'Deploy',id :'tag_id_docker_ce',parameters:[choice(choices: docker_ce, description: 'Select the version of Docker CE', name: 'TAG-docker-ce')]

			containerd_choice = input  message: 'Select the version of containerd IO',ok : 'Deploy',id :'tag_id_containerd',parameters:[choice(choices: containerd_io, description: 'Select the version of containerd IO', name: 'TAG-containerd')]

			docker_ce_cli_choice = input  message: 'Select the version of Docker CE Cli',ok : 'Deploy',id :'tag_id_docker_ce_cli',parameters:[choice(choices: docker_ce_cli, description: 'Select the version of Docker CE Cli', name: 'TAG-docker-cli')]



				}

		}
		else {
			echo "Not an upgarde a first install"
		}
}

def outputCheck() {
	echo "${containerd_choice}"
	echo "${docker_ce_choice}"
	echo "${docker_ce_cli_choice}"

}

///// The End
