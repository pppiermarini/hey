import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*
//'sdlanststmas09cv'['10.42.18.97']

@Library('pipeline-shared-library') _

// git repo if you check in the public key file"
gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git" 
gitBranch="development"
gitCreds="scm-incomm"
artifactDeploymentLoc = "null"
credsName = "scm_deployment"
pubFileName = "pubkeytest"
authorizeKeysFile = "authorized_keys"
tmpLoc = "/tmp"
sshLoc = "/root/.ssh"
STRING = "G5tlHWdzK4SXZga0b"

targetEnv="${target_env}"

targets = [
    'dev-1': ['sdlbifcore01v'],
    'dev-2': ['sdlbifcore02v'],
    'dev1-1': ['sdlaifcore01v'],
    'dev1-2': ['sdlaifcore02v'],
    'qa-1': ['sqlbifcore01v'],
    'qa-2': ['sqlbifcore02v'],
    'qa1-1': ['sqlaifcore01v'],
    'qa1-2': ['sqlaifcore02v'],
	'uat-1': ['sulbifcore01v'],
	'uat-2': ['sulbifcore02v'],
	'uat1-1': ['sulaifcore01v'],
	'uat1-2': ['sulaifcore02v']
	
]

node('linux'){

	stage('checkout'){

	 cleanWs()

	 githubCheckout(gitCreds,gitRepository,gitBranch)

	}
	
	stage('copy and append'){

		deployComponents(targetEnv, targets[targetEnv], "${authorizeKeysFile}")
	}
	
	stage('Test new key'){

		deployComponents2(targetEnv, targets[targetEnv], "${authorizeKeysFile}")
	}
		

}

def deployComponents(envName, targets, authorizeKeysFile){
	echo "${envName}"
	echo "${targets}"
	echo "${authorizeKeysFile}"
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, tmpLoc, authorizeKeysFile) } ]
	}
	parallel stepsInParallel
	
}

def deploy(target_hostname, tmpLoc, authorizeKeysFile) {
	echo "${target_hostname}"
	echo "${tmpLoc}"
	echo "${authorizeKeysFile}"
		sh "ls -ltr automation-scripts/ssh-automation/"
		//sshagent([credsName]) {
		sh """
		   scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no automation-scripts/ssh-automation/${pubFileName} root@${target_hostname}:${tmpLoc}
		   ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -ltr ${tmpLoc}'
		   ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'if  ! grep -q "${STRING}" "${sshLoc}/${authorizeKeysFile}" ; then cat ${tmpLoc}/${pubFileName} >> ${sshLoc}/${authorizeKeysFile} ; else echo 'the string does exist' ; fi'
		   ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -tlr  ${tmpLoc}'
		   ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f  ${tmpLoc}/${pubFileName}'
		   ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -tlr  ${tmpLoc}'
		"""
		//}
}


def deployComponents2(envName, targets, authorizeKeysFile){
	echo "${envName}"
	echo "${targets}"
	echo "${authorizeKeysFile}"
	
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy2(it, tmpLoc, authorizeKeysFile) } ]
	}
	parallel stepsInParallel
	
}

def deploy2(target_hostname, tmpLoc, authorizeKeysFile) {
	echo "${target_hostname}"
	echo "${tmpLoc}"
	echo "${authorizeKeysFile}"
		sh "ls -ltr automation-scripts/source/"
		sshagent([credsName]) {
		sh """
		   ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cat ${sshLoc}/${authorizeKeysFile}'
		"""
		}
}
		 //  ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'cat ${tmpLoc}/${pubFileName} >> ${sshLoc}/${authorizeKeysFile}'
		 //  ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f  ${tmpLoc}/${pubFileName}'
		 
// That's all folks
