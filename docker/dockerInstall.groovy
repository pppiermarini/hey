import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

credsName = "scm_deployment"

targets = [
    'int-a':  ['']
]
  
targetenvs="${targetEnv}"

//General pipeline
emailDistribution="rkale@incomm.com vhari@incomm.com ppiermarini@incomm.com dstovall@InComm.com"


//globals

gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitCreds="scm-incomm"
gitFolder="docker"

pem="docker.maven.incomm.com.pem"
composer="docker-compose"
json="daemon.json"
configFolderLoc="/tmp"
maven_repo= "/etc/yum.repos.d/maven.incomm.com_artifactory_dockerinstall_7_x86_64_stable_.repo"
docker_repo= "/etc/yum.repos.d/docker.maven.incomm.com_artifactory_scmrpm.repo"
ca_cert = "/etc/pki/ca-trust/source/anchors/"
url_docker ="https://maven.incomm.com/artifactory/dockerinstall/7/x86_64/stable/"
url_art="https://docker.maven.incomm.com/artifactory/scmrpm"
docker_loc="/etc/docker/"
local_bin_loc="/usr/local/bin/"

currentBuild.result = 'SUCCESS'

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

containerd_io = []
docker_ce = []
docker_ce_cli = []
docker_ce_rootless = [] // extra, not part of use case
docker_ce_selinux = [] // extra, not part of use case

upgrade_target = []
install_target = []

docker_ce_choice = ""
containerd_choice =""
docker_ce_cli_choice =""

docker_ce_latest="docker-ce-20.10.5"
docker_ce_cli_latest="docker-ce-cli-20.10.5"
docker_containerd="containerd.io"

isUpgrade=""

node('linux'){
	try { 
		cleanWs()			
		//select the artifact

		stage('Check Docker installation') {
			checkDocker(targetEnv, targets[targetEnv])
		}

		stage('Checking for Docker Upgrade'){
		if(!upgrade_target.isEmpty()) {
			getInfo()
			outputCheck()
			upgradeDockerVersion(targetEnv, upgrade_target)
		}

		} // end Upgrade Request stage
 
		stage('Git checkout'){
		if(!install_target.isEmpty()) {
			githubCheckout(gitCreds,gitRepository,gitTag)
		}
			
		} // Gitcheckout stage

		stage("Updating yum config"){
		if (!install_target.isEmpty()) {
			updateYumConfigs(targetEnv, install_target)
		}
		

		} // end updating yum config 

		stage("Install Docker"){
		if (!install_target.isEmpty()) {
			installDocker(targetEnv, install_target)
		}
			
		}  // end install docker stage

		stage("Update Docker configs"){
		if (!install_target.isEmpty()) {
			addDockerConfig(targetEnv, install_target)
		}
			
		}  // end updating docker config
		
		stage("Cleanup"){
			initiateCleanup(targetEnv, targets[targetEnv])

		} // end cleanup stage
		
	} catch (exc) {

			currentBuild.result = 'FAILURE'
			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
	}
	} //end finally
}// end node

def updateYumConfigs(envName, targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployYumConfigs(it, envName) } ]
	}
	parallel stepsInParallel
	
}


def deployYumConfigs(target_hostname, envName) {

	sshagent([credsName]) {

	echo "Adding pem, repo and updating certs"
	sh """
	scp -o StrictHostKeyChecking=no -r ${gitFolder}/${pem} root@${target_hostname}:${configFolderLoc}
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp ${configFolderLoc}/${pem} ${ca_cert}'
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'update-ca-trust'
	"""
	sleep(5)

	echo "Adding repos to yum config"
	sh"""
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum install -y yum-utils'
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum-config-manager --add-repo ${url_docker}'
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum-config-manager --add-repo ${url_art}'
	"""

	echo "Addings Credentials to docker and maven repos"
	withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "username=${USER}" >> ${maven_repo}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "password=${PASS}" >> ${maven_repo}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "username=${USER}" >> ${docker_repo}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "password=${PASS}" >> ${docker_repo}'
	"""   }

		}


}

def upgradeDockerVersion(envName, targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployupgradeDockerVersion(it, envName) } ]
	}
	parallel stepsInParallel
	
}


def deployupgradeDockerVersion(target_hostname, envName) {
	sshagent([credsName]) {
	sh """
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum-config-manager --add-repo ${url_docker}'
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum-config-manager --add-repo ${url_art}'"""

	withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "username=${USER}" >> ${maven_repo}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "password=${PASS}" >> ${maven_repo}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "username=${USER}" >> ${docker_repo}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'echo "password=${PASS}" >> ${docker_repo}'
	""" }

	sleep(2)

	sh"""ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum -y upgrade ${docker_ce_choice} ${docker_ce_cli_choice} ${containerd_choice}'"""

	sh """ ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload'"""
	sleep(2)

	sh """ ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl restart docker'"""

	sleep(2)
	sh """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl enable docker'"""
	
	echo "Docker login"
	withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
	sh """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker login docker.maven.incomm.com --username ${USER} --password ${PASS}'""" }


	}
}


def installDocker(envName, targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployinstallDocker(it, envName) } ]
	}
	parallel stepsInParallel
	
}


def deployinstallDocker(target_hostname, envName) {
		//sed -i 's/gpgcheck=1/gpgcheck=0/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum install -y ${docker_ce_latest} ${docker_ce_cli_latest} ${docker_containerd}'
			"""
			sleep(2)
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl start docker'
			"""
			sleep(2)
			"Docker version"
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker --version'
			"""
			

			}

}

def addDockerConfig(envName, targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployaddDockerConfig(it, envName) } ]
	}
	parallel stepsInParallel
	
}


def deployaddDockerConfig(target_hostname, envName) {
		//sed -i 's/gpgcheck=1/gpgcheck=0/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {
			sh """
			scp -o StrictHostKeyChecking=no -r ${gitFolder}/${json} root@${target_hostname}:${configFolderLoc}
			scp -o StrictHostKeyChecking=no -r ${gitFolder}/${composer} root@${target_hostname}:${configFolderLoc}
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp ${configFolderLoc}/${json} ${docker_loc}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp ${configFolderLoc}/${composer} ${local_bin_loc}'
			"""
			sleep(2)
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload'
			"""
			sleep(2)
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl restart docker'
			"""

			sleep(2)
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl enable docker'
			"""
			echo "Docker login"
			withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
			sh """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker login docker.maven.incomm.com --username ${USER} --password ${PASS}'""" }

		}
		
}


def initiateCleanup(envName, targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployinitiateCleanup(it, envName) } ]
	}
	parallel stepsInParallel
	
}


def deployinitiateCleanup(target_hostname, envName) {
		//sed -i 's/gpgcheck=0/gpgcheck=1/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm ${maven_repo}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm ${docker_repo}'
			"""
		}

}


def checkDocker(envName, targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploycheckDocker(it, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploycheckDocker(target_hostname, envName) {
		//sed -i 's/gpgcheck=0/gpgcheck=1/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {

		check = sh(script: """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'command -v docker'""", returnStdout: true).trim()
		if ("${check}" =="/usr/bin/docker") {
			echo "Docker is already installed on the machine, going to upgrade stage"
			isUpgrade = "Yes"
			upgrade_target.add(target_hostname)

		}
		else {
			echo "Docker is not installed, proceeding to installation"

			isUpgrade = "No"
			install_target.add(target_hostname)

		}
		//echo response

	}

}

def getInfo(){
	if(isUpgrade == "Yes") {
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
				if (i.contains("containerd.io")) {
					containerd_io.add(i)
				} 
		
				if (i.matches("docker-ce-([0-9].+)")) {
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

			echo "Choose a containerd IO"

			containerd_choice = input  message: 'Select the version of containerd IO',ok : 'Deploy',id :'tag_id_containerd',parameters:[choice(choices: containerd_io, description: 'Select the version of containerd IO', name: 'TAG-containerd')]

			echo "Choose a Docker CE Cli"

			docker_ce_cli_choice = input  message: 'Select the version of Docker CE Cli',ok : 'Deploy',id :'tag_id_docker_ce_cli',parameters:[choice(choices: docker_ce_cli, description: 'Select the version of Docker CE Cli', name: 'TAG-docker-cli')]



				}
		}
		else {
			"Something went wrong please check.."
		}

}

def outputCheck() {
	echo "${containerd_choice}"
	echo "${docker_ce_choice}"
	echo "${docker_ce_cli_choice}"

}

///// The End