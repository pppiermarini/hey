import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git" 
gitBranch="origin/development"
gitCreds="scm-incomm"

emailDistribution="rkale@incomm.com" 
// vhari@incomm.com ppiermarini@incomm.com dstovall@incomm.com
//:443
artifactory="https://maven.incomm.com/artifactory/"

now = new Date()
nowFormatted = now.format("MM-dd-YYYY", TimeZone.getTimeZone('UTC'))

repoFileName="allrepos.py"

node('linux2'){
	try { 
        cleanWs()

		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}
		
		stage('Initiate Cleanup') {
			echo "Initiate a cleanup"
			repoFile="${WORKSPACE}/utils/artifactory/repos/allrepos.py"

			withCredentials([usernamePassword(credentialsId: 'art-scm-cleanup', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
						echo "Show all items that will be deleted"

						sh """
						chmod 755 ${WORKSPACE}/utils/artifactory/repos/allrepos.py
						cd ${WORKSPACE}/utils/artifactory/repos/
						ls -la
						artifactory-cleanup --user ${USER} --password ${PASS} --artifactory-server ${artifactory} --config ${repoFileName}"""

				}

		}

	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
    //Sending a bunch of information via email to the email distro list of participants	

    //sendEmailNotificationBuild("${emailDistribution}", env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME)	
	}
		
}  //end of node

def sendEmailNotificationBuild(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME) {
	if (currentBuild.currentResult == "SUCCESS"){
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Artifactory cleanup job: ${JOB_NAME}", 
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
        subject: "Artifactory cleanup job: ${JOB_NAME}", 
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
	else if (currentBuild.currentResult == "FAILURE"){
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Artifactory cleanup job: ${JOB_NAME}", 
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
	else{
		
		echo "Issue with System Results please check"
		
	}
}

///// The End
