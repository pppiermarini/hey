import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


// git repo if you check in the public key file"
gitRepository=""
gitBranch="development"
gitCreds="scm-incomm"
artifactDeploymentLoc = "null"
credsName = "scm_deployment"
pubFileName = "pubkeytest.txt"
authorizeKeysFile = "testfile.txt"
tmpLoc = "/tmp"

targetEnv="${target_env}"

targets = [
    'sdlanststmas09cv': ['10.42.18.97'],
]



node('linux'){

stage('checkout'){

 cleanWs()

 githubCheckout(gitCreds,gitRepository,gitBranch)


	stage('copy and append'){
		deployComponents(targetEnv, targets[targetEnv], "${authorizeKeysFile}")
	}
	
}


def deployComponents(envName, targets, authorizeKeysFile){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, authorizeKeysFile) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, authorizeKeysFile) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		scp -q -o StrictHostKeyChecking=no ${pubFileName} root@${target_hostname}:${tmpLoc}		 
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cat ${pubFileName} >> ${authorizeKeysFile}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f  ${tmpLoc}/${pubFileName}'
		"""
	}
}