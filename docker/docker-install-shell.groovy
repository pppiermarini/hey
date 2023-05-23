import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


emailDistribution="rkale@incomm.com vhari@incomm.com"
//General pipeline 
credsName = 'scm_deployment'

targetenvs="${targetEnv}"

imageDeploymentLoc="/app/docker/tmp"

dockercompose="docker-compose"

configLoc="/app/docker-install"

commandFile="install-docker.sh"

deamonFile="daemon.json"

permFile="docker.maven.incomm.com.pem"


gitLoc = "docker"

gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="refs/heads/development"
gitCreds="scm-incomm"

//Refer to: https://wiki.incomm.com/pages/viewpage.action?pageId=141008525
//@TODO work with team to get a key value for CAT1: 
//'10.41.4.218','10.41.4.219','10.41.4.247','10.41.4.248','10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207'
//'pre-prod': ['10.41.4.218','10.41.4.219',]
//	'dev':  ['10.40.7.42','10.40.7.43','10.40.7.44'],
//	'pre-prod': ['10.41.4.247','10.41.4.248']
//'10.41.6.97','10.41.6.98','10.41.6.203','10.41.6.204','10.41.6.205','10.41.6.206','10.41.6.207'
targets = [
	'dev': ['10.42.18.240']
	]


approvalData = [
	'operators': "[ppiermarini,vhari,dstovall]",
	'adUserOrGroup' : 'ppiermarini,vhari,dstovall',
    'target_env': targetenvs //Update to add env
]

node('linux1'){
	try { 
		
		cleanWs()
		//select the artifact 
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
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker load -i ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'docker import ${imageDeploymentLoc}/${imageName}-${imageLabel}.tar'
	//scp -q -o StrictHostKeyChecking=no ${gitLoc}/${dockercompose} root@${target_hostname}:${configLoc}/

	// 

	sshagent([credsName]) {
		echo "Deploying the install file"

	    sh"""
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${configLoc}'
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${commandFile} root@${target_hostname}:${configLoc}/
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${deamonFile} root@${target_hostname}:${configLoc}/
	    	 scp -q -o StrictHostKeyChecking=no ${gitLoc}/${permFile} root@${target_hostname}:${configLoc}/
	    	 ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 -R ${configLoc}'
		     ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/cd ${configLoc} && /bin/sh ${configLoc}/${commandFile}'
		"""
	}

}



///// The End
