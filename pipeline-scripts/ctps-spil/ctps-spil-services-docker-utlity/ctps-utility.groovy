import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


emailDistribution="rkale@incomm.com vhari@incomm.com mpalve@incomm.com vpilli@incomm.com psubramanian@incomm.com nkassetty@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

targetenvs="${targetEnv}"

configLoc="/app/docker-utility"

commandFile="command.sh"


gitLoc = "copy"

gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-docker-utlity.git"
gitBranch="${BRANCH}"
gitCreds="scm-incomm"

targets = [
	'dev':  ['10.42.20.20','10.42.20.21','10.42.16.106','10.42.16.107'],
    'qa': ['10.42.82.92','10.42.82.93','10.42.82.239','10.42.82.240'],
    'uat':['10.42.50.93','10.42.50.94','10.42.50.200','10.42.50.201'],
    'pre-prod':['10.40.7.214','10.40.7.215','10.40.7.229','10.40.7.230'],
    'prod':['10.40.7.210','10.40.7.211','10.40.7.212','10.40.7.213','10.40.7.231','10.40.7.232','10.40.7.233']
	]

approvalData = [
	'operators': "[ppiermarini,vhari,mpalve,vpilli,psubramanian]",
	'adUserOrGroup' : 'ppiermarini,vhari,mpalve,vpilli,psubramanian',
    'target_env': '${targetenvs}' //Update to add env
]

node('linux'){
	try { 
		
		cleanWs()
		stage('Approval') {
                if (targetenvs == "uat" || targetenvs == "pre-prod" || targetenvs == "prod") {
                    getApproval(approvalData)       
                } else {
                    echo "Deploying to ${targetenvs} doesn't required any approvals"
                }
            }
     
     	stage('Github checkout'){
			echo "Checking out Git install repo"
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}

		stage("Deploy to ${targetenvs}") {
			echo "{$targetenvs}"
			deployComponents(targetenvs, targets[targetenvs])

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


def deployComponents(envName, targets){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, envName) } ]
	}
	parallel stepsInParallel


	
}

def deploy(target_hostname, envName) {
	echo " the target is: ${target_hostname}"

	sshagent([credsName]) {
		echo "Deploying the install file"

	    sh"""
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${commandFile} root@${target_hostname}:${configLoc}/
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/cd ${configLoc} && /bin/sh ${configLoc}/${commandFile}'
		"""
	}

}



///// The End
