import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


//GLOBALS
gitRepository="https://github.com/InComm-FSWEB/FSWEB-YourRewardCard-UI.git"
gitBranch="origin/thescox-incomm-snyk-test"
gitCreds="scm-incomm"

snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"

projectName="FSWEB-YourRewardCard-UI"


node('linux2'){
	cleanWs() //keep for initial debugging
	try { 

    	stage('Github checkout') {
   			githubCheckout(gitCreds,gitRepository,gitBranch)

    	}

    	stage('Snyk Auth for FSWEB -- Code Test') {
        	echo "Snyk auth for FSWEB -- Code Test run"
        	
        	withCredentials([string(credentialsId: 'snyk-fsweb', variable: 'AUTH')]) {
				
				sh """
				${snyk} auth ${AUTH}
				${snyk} code test --json | ${snykHtml} -o ${projectName}-code.html
				"""

				def statusCode = sh(script: "${snyk} code test --severity-thershold=high", returnStdout: true).trim()
				/*if (statusCode != 0) {
					throw new Exception("Please check the vul. HTML ${projectName}-code.html")

				} */
			}

        }

	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
    	echo "End of pipeline"
    //sendCertNotification(projectProperties.email.emailDistribution, env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME, projectProperties.imageInfo.imageName, labelScan)	
	}
		
}  //end of node






///// The End
