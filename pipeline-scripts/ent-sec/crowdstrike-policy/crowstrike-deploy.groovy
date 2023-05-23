import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository="https://github.com/incomm-iac/crowdstrike-installers.git" 
gitBranch="origin/master"
gitCreds="scm-incomm"

emailDistribution="rkale@incomm.com vhari@incomm.com soneill@incomm.com lterrell@incomm.com" //soneill@incomm.com lterrell@incomm.com

now = new Date()
nowFormatted = now.format("MM-dd-YYYY", TimeZone.getTimeZone('UTC'))

jfrog = "/usr/bin/jfrog"

crowdstrikeArtRepo = "crowdstrike/" 

node('linux2'){
	try { 
		
		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}


		stage('Downloading Crowdstrike Installers'){
			dir ('crowdstrike-installers') {
				sh """echo "The installer versions are downloaded on ${nowFormatted} from Crowdstrike"  > version.txt"""

					dir('Windows') {
				
				withCredentials([string(credentialsId: 'crowdstrike-client-id', variable: 'CID'),
            		string(credentialsId: 'crowdstrike-secret', variable: 'SECRET')]) {
				sh """
					python3 ${WORKSPACE}/installer_download.py --client ${CID} --secret ${SECRET} --platform Windows --download
				"""
			}

			}

			dir('Mac') {
				
				withCredentials([string(credentialsId: 'crowdstrike-client-id', variable: 'CID'),
            		string(credentialsId: 'crowdstrike-secret', variable: 'SECRET')]) {
				sh """
					python3 ${WORKSPACE}/installer_download.py --client ${CID} --secret ${SECRET} --platform Mac --download
				"""
				}
				
			}

			dir('Linux') {

				dir('6') {
					
					withCredentials([string(credentialsId: 'crowdstrike-client-id', variable: 'CID'),
            		string(credentialsId: 'crowdstrike-secret', variable: 'SECRET')]) {
					sh """
						python3 ${WORKSPACE}/installer_download.py --client ${CID} --secret ${SECRET} --platform Linux --version 6 --distro CentOS --download
					"""}
				}
				dir('7') {

					
					withCredentials([string(credentialsId: 'crowdstrike-client-id', variable: 'CID'),
            		string(credentialsId: 'crowdstrike-secret', variable: 'SECRET')]) {
					sh """
						python3 ${WORKSPACE}/installer_download.py --client ${CID} --secret ${SECRET} --platform Linux --version 7 --distro CentOS --download
					"""}
				}
				dir('8') {
				    
					withCredentials([string(credentialsId: 'crowdstrike-client-id', variable: 'CID'),
            		string(credentialsId: 'crowdstrike-secret', variable: 'SECRET')]) {
					sh """
						python3 ${WORKSPACE}/installer_download.py --client ${CID} --secret ${SECRET} --platform Linux --version 8 --distro CentOS --download
					"""}

				}
			}
		
			}

		}
        
        stage('Upload to Artifactory') {
			echo "Uploading to artifactory"
			sh """
			cd ${WORKSPACE}
			${jfrog} rt u crowdstrike-installers/*.txt ${crowdstrikeArtRepo} --include-dirs=true
			${jfrog} rt u crowdstrike-installers/Windows/*.exe ${crowdstrikeArtRepo} --include-dirs=true
			${jfrog} rt u crowdstrike-installers/Mac/*.pkg ${crowdstrikeArtRepo} --include-dirs=true
			${jfrog} rt u crowdstrike-installers/Linux/6/* ${crowdstrikeArtRepo} --include-dirs=true
			${jfrog} rt u crowdstrike-installers/Linux/7/* ${crowdstrikeArtRepo} --include-dirs=true
			${jfrog} rt u crowdstrike-installers/Linux/8/* ${crowdstrikeArtRepo} --include-dirs=true
			"""
        }
        
        stage('Cleanup') {
        	cleanWs()
        }

	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"

    } finally {
    //Sending a bunch of information via email to the email distro list of participants	

    sendEmailNotificationBuildDocker("${emailDistribution}", env.BUILD_NUMBER, env.JOB_URL, env.JOB_NAME)	
	}
		
}  //end of node

def sendEmailNotificationBuildDocker(def emailDistribution, def BUILD_NUMBER, def JOB_URL, def JOB_NAME) {
	if (currentBuild.currentResult == "SUCCESS"){
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Upload Crowdstrike Installers job: ${JOB_NAME}", 
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
        subject: "Upload Crowdstrike Installers job: ${JOB_NAME}", 
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
        subject: "Upload Crowdstrike Installers job: ${JOB_NAME}", 
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
