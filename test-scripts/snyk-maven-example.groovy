import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//https://github.com/InComm-Software-Development/ctps-spil-services-vix.git
gitRepository="${GIT_REPOSITORY}"
gitBranch="${Branch}"
gitCreds="scm-incomm"
emailDistribution="rkale@incomm.com vhari@incomm.com dstovall@InComm.com ppiermarini@incomm.com"
//userInput = "${BUILD_TYPE}"
//General pipeline

registry="docker.maven.incomm.com"
//apache-maven-3.5.0 -> build-tst
//apache-maven-3.3.9 -> build incomm
maven="mvn"
snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"
scanCodeDir="reports-code"
scanContainerDir="reports-container"
vulnerabilityFound=""
imageLabel=""
labelScan=""
jfrog = "/usr/bin/jfrog"
//currentBuild.result = "SUCCESS"
//master
node('linux2'){
	try { 
		cleanWs()

		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}//stage

        ///usr/bin/snyk-linux test --json

        stage('Snyk Auth for Ctps') {
        	echo "Snyk auth for ctps"
        	
        	withCredentials([string(credentialsId: 'snyk-ctps', variable: 'AUTH')]) {
				sh "${snyk} auth ${AUTH}"
			}

        }

		stage('Scaning Code and Container'){
				echo "Local scan initiated"
				pom = readMavenPom file: 'pom.xml'
				labelScan = pom.getVersion();
				sh """
				${snyk} code test --json | ${snykHtml} -o demo-${labelScan}-code.html
				${snyk} test --json | ${snykHtml} -o demo-${labelScan}-third-party.html
				"""
		}//stage
		
		stage('Publishing Reports') {
			
			
			dir("${scanCodeDir}") {
				sh """
				mv ${WORKSPACE}/demo-${labelScan}-code.html ${WORKSPACE}/${scanCodeDir}
				mv ${WORKSPACE}/demo-${labelScan}-third-party.html ${WORKSPACE}/${scanCodeDir}

				ls -ltra 
				"""
			}

	 			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "demo-${labelScan}-code.html",
	 			reportName: "Report for demo-code",
	 			reportTitles: "The Report of demo-code"])

	 			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-code',
	 			reportFiles: "demo-${labelScan}-third-party.html",
	 			reportName: "Report for demo-third-party",
	 			reportTitles: "The Report of demo-third-party"])
		}


	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
  
	}
		
}  //end of node

