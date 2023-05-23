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


//Git checkout params
gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="development"
gitCreds="scm-incomm"

//Script loc
scriptLocWin = "E:\\jenkins-home\\workspace\\Telnet\\Telnet-Pipeline\\automation-scripts\\ps-scripts\\Network"
scriptLocLinux = "/app/jenkins/workspace/Telnet/Telnet-Pipeline/automation-scripts/ps-scripts/Network"



//Nodes and Label setup
stage('checkout') {
if (userInput == 'windows-node') {
	node('windows'){
		
		try {
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
			hostNameSplit.each {
				bat"""
				powershell ${scriptLocWin}\\TelnetTest.ps1 $it ${myPort}
				"""
		}	
		//catch any exception 
		} 	catch(exc) {	throw exc } 

	} //end of node
}


else {
		node('linux'){
		try { 
		cleanWs()
		githubCheckout(gitCreds,gitRepository,gitBranch)
		sh """
		echo "Starting the script"
		/bin/chmod 755 ${scriptLocLinux}/TelnetTest.sh
		"""
		hostNameSplit.each {
			sh"""
			${scriptLocLinux}/TelnetTest.sh $it ${myPort}
			"""
		}
		//catch any exception 
		} catch(exc) {	throw exc } 

	} //end of node

	}
}