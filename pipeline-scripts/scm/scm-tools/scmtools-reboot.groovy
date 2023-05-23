import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _
credsName = "scm_deployment"

gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="origin/development"
gitCreds="scm-incomm"

//@TODO: Add email notification
emailDistribution="rkale@incomm.com" //scm@incommm.com

ansibleDir = "/home/svc_scm_ansible"

node('linux1'){
	cleanWs() //keep for initial debugging


	try { 

    	stage('Git Checkout') {	
    		githubCheckout(gitCreds,gitRepository,gitBranch)
    	}

    	stage('Test') {
			/*
			sh """
    			ls -ltra ${WORKSPACE}/utils/ansible/scm-reboots/
				cd ${WORKSPACE}/utils/ansible/scm-reboots/ && ansible-playbook -i devhosts ping.yml
    			whoami
    		"""*/

			sshagent([credsName]) {
			sh """	
			ssh -q -o StrictHostKeyChecking=no root@sdlanststmas09cv 'su svc_scm_ansible && whoami > ${ansibleDir}/user.txt'
			"""
			}
			/*
			withCredentials([usernamePassword(credentialsId: 'ansible-scm', passwordVariable: 'PASS', usernameVariable: 'USER')]) {

			sh """
    		ls -ltra ${WORKSPACE}/utils/ansible/scm-reboots/
			echo "${PASS}" | su ${USER}
    		whoami
    		"""
				//call ansible playbook here
			
			}*/
    	}




	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
   		sendNotification(emailDistribution,env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME)
	}
		
}  //end of node


def sendNotification(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME) {
	echo "${emailDistribution}"
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
    emailext attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>

				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if (currentBuild.currentResult == "ABORTED"){
		echo "aborted"
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""						
	}
	else if(currentBuild.currentResult == "SUCCESS"){
		echo "success"
		echo "${emailDistribution}"
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
			body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""			
	}
}

///// The End
