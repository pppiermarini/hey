import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//Variable configs
myHosts = "${HOSTS}"
myPort = "${PORT}"

// inputs from build with parameters
userInput="${ENVIRONMENT}"

//Splitting each host based on comma
hostNameSplit = myHosts.split(",")
portSplit = myPort.split(",")


//Git checkout params
gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="origin/development"
gitCreds="scm-incomm"

//Script loc
//scriptLocWin = "E:\\jenkins-home\\workspace\\Telnet\\Telnet-All-Nodes\\automation-scripts\\ps-scripts\\Network"
//scriptLocLinux = "/app/jenkins/workspace/Telnet/Telnet-All-Nodesv2/automation-scripts/ps-scripts/Network"



//Nodes and Label setup
stage('checkout') {
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

else {
	node(userInput){
		try { 
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
			sh """
				/bin/chmod 755 ${WORKSPACE}/automation-scripts/ps-scripts/Network/TelnetTestv2.sh
			"""
			for(host in hostNameSplit) {
				for(port in portSplit) {
				sh """
				${WORKSPACE}/automation-scripts/ps-scripts/Network/TelnetTestv2.sh $host $port
				"""
				}
		}
		//catch any exception 
			} catch(exc) {	throw exc } 

		} //end of node

	}

}