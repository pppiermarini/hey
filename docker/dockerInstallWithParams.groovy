import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


//This install script is heavily inspired by the steps at https://docs.docker.com/engine/install/centos/

@Library('pipeline-shared-library') _

credsName = "scm_deployment"

  

//General pipeline
emailDistribution="vhari@incomm.com ppiermarini@incomm.com dstovall@InComm.com jrivett@incomm.com"



//Setting Repo information.
gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitCreds="scm-incomm"
gitFolder="docker"
gitBranch="origin/development"
//All Docker Globals
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

// Adding empty lists to hold upgrade targets vs. install targets based on logic
upgrade_target = []
install_target = []

//Empty strings for the inputs 
docker_ce_choice = ""
containerd_choice =""
docker_ce_cli_choice =""

//splitting based on comma
targetenvs="${HOST}".split(',')

//storing operation input
operations="${OPERATIONS}"

//storing docker version
docker_version= "${DOCKER_VERSION}"

//empty list to add all targets provided by user
targets = []

//empty string to check 
//isUpgrade=""

//set the value of Host for checking later
isHostEmpty = "${HOST}"

node('linux1'){
	try { 
		cleanWs()			
		//Add all targets if not empty
		//Git checkout
		stage('Git checkout'){
		
		githubCheckout(gitCreds,gitRepository,gitBranch)

		} // Gitcheckout stage
		stage('Adding all host boxes') {
			if(isHostEmpty != null && !isHostEmpty.isEmpty()) {
			addHosts()
			echo "All hosts: ${targets}"
			}
			else {
				currentBuild.result = 'FAILURE'
				error('Build failed because supplied target list is empty')
			}
		}
		//Check if docker is installed on targets
		stage('Check Docker installation') {
			checkDocker(targets)
		}
		//Setting the input parameters after sanitizing
		stage('Add User Inputs') {
			setInfo()
			outputCheck()
		}
		//Upgrade stage
		stage('Checking for Docker Upgrade'){
		if(!upgrade_target.isEmpty() && "${operations}" != "Uninstall") {
			upgradeDockerVersion(upgrade_target)
		}
		else {
			echo "Not a Docker Upgrade"
		}

		} // end Upgrade Request stage
 		

		//Updaing yum configs for a first time install
		stage("Updating yum config"){
		if (!install_target.isEmpty()) {
			updateYumConfigs(install_target)
		}
		else {
			echo "Not an install"
		}
			

		} // end updating yum config

		//Install docker using yum

		stage("Install Docker"){
		if (!install_target.isEmpty()) {
			installDocker(install_target)
		}
		else {
			echo "Not an install"
		}
		}  // end install docker stage

		//Updating docker config

		stage("Update Docker configs"){
		if (!install_target.isEmpty()) {
			addDockerConfig(install_target)
		}
		else {
			echo "Not an install"
		}
			
			
		}  // end updating docker config
		
		//Uninstall docker if user chosen operation is Uninstall
		
		stage("Check Uninstall Docker") {
			if("${operations}" == "Uninstall") {
				removeDocker(targets)
			}
			else {
				echo "Not an Uninstall"
			}
		}

		//cleanup
		stage("Cleanup"){
			initiateCleanup(targets)

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

def updateYumConfigs(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployYumConfigs(it) } ]
	}
	parallel stepsInParallel
	
}


def deployYumConfigs(target_hostname) {

	sshagent([credsName]) {

	echo "Adding pem, repo and updating certs"
	sh """

	scp -o StrictHostKeyChecking=no -r ${gitFolder}/${pem} root@${target_hostname}:${configFolderLoc}
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yes | cp ${configFolderLoc}/${pem} ${ca_cert}'
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'update-ca-trust'
	"""
	sleep(5)

	echo "Adding repos to yum config"
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${maven_repo}" ]; then rm "${maven_repo}"; fi'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${docker_repo}" ]; then rm "${docker_repo}"; fi'"""

		check_utils = sh(script: """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum list --installed | grep yum-utils | wc -l'""", returnStdout: true).trim()
		if ("${check_utils}" == "0") {
			echo "Installing yum utils"
			//isUpgrade = "Yes"
			//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum install -y yum-utils'

			sh"""
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
			"""   
			}

		}
		else {
			echo "Yum utils is installed and configured"

		}	


		}

}

def upgradeDockerVersion(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployupgradeDockerVersion(it) } ]
	}
	parallel stepsInParallel
	
}


def deployupgradeDockerVersion(target_hostname) {
	sshagent([credsName]) {


	echo "Adding pem, repo and updating certs"
	sh """
	scp -o StrictHostKeyChecking=no ${gitFolder}/${pem} root@${target_hostname}:${configFolderLoc}
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yes | cp ${configFolderLoc}/${pem} ${ca_cert}'
	ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'update-ca-trust'
	"""
	sleep(5)

	echo "Adding repos to yum config"
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${maven_repo}" ]; then rm "${maven_repo}"; fi'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${docker_repo}" ]; then rm "${docker_repo}"; fi'"""

		check_utils = sh(script: """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum list --installed | grep yum-utils | wc -l'""", returnStdout: true).trim()
		if ("${check_utils}" == "0") {
			echo "Installing yum utils"
			//isUpgrade = "Yes"
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
			"""   
			}

		}
		else {
			echo "Yum utils is installed and configured"

		}

	sleep(2)
	sh"""ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum -y upgrade ${docker_ce_choice} ${docker_ce_cli_choice} ${containerd_choice} --nogpgcheck'"""

	sleep(2)

	sh """ ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl start docker'"""

	sleep(2)

	echo "Docker login"
	withCredentials([usernamePassword(credentialsId: 'svc_docker_ro', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
	sh """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker login docker.maven.incomm.com --username ${USER} --password ${PASS}'""" }
	}
}


def installDocker(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployinstallDocker(it) } ]
	}
	parallel stepsInParallel
	
}


def deployinstallDocker(target_hostname) {
		//sed -i 's/gpgcheck=1/gpgcheck=0/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum -y install ${docker_ce_choice} ${docker_ce_cli_choice} ${containerd_choice} --nogpgcheck'
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

def addDockerConfig(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployaddDockerConfig(it) } ]
	}
	parallel stepsInParallel
	
}


def deployaddDockerConfig(target_hostname) {
		//sed -i 's/gpgcheck=1/gpgcheck=0/g' /etc/yum.conf --> Unsure if this is needed
		//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yes | cp ${configFolderLoc}/${composer} ${local_bin_loc}'
		//scp -o StrictHostKeyChecking=no -r ${gitFolder}/${composer} root@${target_hostname}:${configFolderLoc}
		sshagent([credsName]) {
			sh """
			scp -o StrictHostKeyChecking=no -r ${gitFolder}/${json} root@${target_hostname}:${configFolderLoc}
			
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yes | cp ${configFolderLoc}/${json} ${docker_loc}'
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


def initiateCleanup(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployinitiateCleanup(it) } ]
	}
	parallel stepsInParallel
	
}


def deployinitiateCleanup(target_hostname) {
		//sed -i 's/gpgcheck=0/gpgcheck=1/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {
			
			echo "Removing repos if exits.."
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${maven_repo}" ]; then rm "${maven_repo}"; fi'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${docker_repo}" ]; then rm "${docker_repo}"; fi'
			"""

			echo "Cleanup completed"
		}

}


def checkDocker(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploycheckDocker(it) } ]
	}
	parallel stepsInParallel
	
}


def deploycheckDocker(target_hostname) {
		//sed -i 's/gpgcheck=0/gpgcheck=1/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {

		check = sh(script: """ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -l /usr/bin/docker | wc -l'""", returnStdout: true).trim()
		if ("${check}" == "1") {
			echo "Docker is already installed on the machine, adding to upgrade targets"
			//isUpgrade = "Yes"
			upgrade_target.add(target_hostname)

		}
		else {
			echo "Docker is not installed, adding to install targets"

			//isUpgrade = "No"
			install_target.add(target_hostname)

		}
		//echo response

	}

}


def removeDocker(targets){
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployremoveDocker(it) } ]
	}
	parallel stepsInParallel
	
}


def deployremoveDocker(target_hostname) {
		//sed -i 's/gpgcheck=0/gpgcheck=1/g' /etc/yum.conf --> Unsure if this is needed
		sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'yum -y remove docker-ce docker-ce-cli containerd.io docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf /var/lib/docker'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf /var/lib/containerd'
		"""
		sleep(2)
		echo "Docker is successfully uninstalled on ${target_hostname}"
	}

}


def addHosts() {
	for (i in targetenvs) {
			targets.add(i.trim())
	}

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