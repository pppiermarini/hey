import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// inputs from build with parameters
userInput="${ENVIRONMENT}"

emailDistribution="jrivett@incomm.com"


//Git checkout params
gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="origin/development"
gitCreds="scm-incomm"

//Script loc
//scriptLocWin = "E:\\jenkins-home\\workspace\\Telnet\\Telnet-All-Nodes\\automation-scripts\\ps-scripts\\Network"
//scriptLocLinux = "/app/jenkins/workspace/Telnet/Telnet-All-Nodesv2/automation-scripts/ps-scripts/Network"



//Nodes and Label setup
node(userInput){
stage('checkout') {

/*
if (userInput == 'windows' || userInput == ".NET") {
	node(userInput){
		try {
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
			for(host in hostNameSplit) {
				for(port in portSplit) {
				bat"""
				powershell ${WORKSPACE}\\automation-scripts\\ps-scripts\\Network\\TelnetTest.ps1 $host $port
				"""
				}
		}	
		//catch any exception 
		} 	catch(exc) {	throw exc } 

	} //end of node
}
*/

	
	try { 
		cleanWs()
		githubCheckout(gitCreds,gitRepository,gitBranch)
		sh """
			/bin/chmod 755 ${WORKSPACE}/utils/CheckSpace.sh
		"""
		sh """
			${WORKSPACE}/utils/CheckSpace.sh
		"""
	}

	//catch any exception 
		catch(exc) { throw exc } 
		finally {
			if (currentBuild.currentResult == "FAILURE"){
				echo "if failure"
				emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"
			} else if(currentBuild.currentResult == "SUCCESS"){
				echo "if success"
				emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"
			}else{
				echo "LAST"
			}
		}

	} //end of stage
} //end of node