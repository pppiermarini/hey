import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

targetEnv="${target_env}"

gitBranch="${BRANCH}"
gitRepository="https://github.com/InComm-Software-Development/ddc-merchants.git"
gitCreds="scm-incomm"
credsName="scm_deployment"

targets = [
    'dev':  ['sdddcwebapp468v.unx.incommtech.net', 'sdddcwebapp469v.unx.incommtech.net'],
]
asterik = "*"
emailDistribution="ppiermarini@incomm.com"

artifactDeploymentLoc ="/var/www/ddc-static/"
user = "svc_springboot"
group = "svc_springboot"

maven="/opt/apache-maven-3.2.1/bin/mvn"

//linux1 = spscmbuild01v
//linux2 = spscmbuild07v
node('linux1'){

		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
		
		stage('Build') {

			echo 'building project...'
			sh "${maven} clean compile"
		}
		
		stage('Deployment'){
	
			deployComponents(targetEnv, targets[targetEnv])
		}

}

/////////////////////////////////////////////////////////////
/*
*/
def deployComponents(envName, targets){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc) } ]
	}
	parallel stepsInParallel
	
}

def deploy(target_hostname, artifactDeploymentLoc) {
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}${asterik}'
		scp -r -q -o StrictHostKeyChecking=no target/bestbuy/ root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no target/costco/ root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no target/default/ root@${target_hostname}:${artifactDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}'
		"""
	}
}