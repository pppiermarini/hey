import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _


//globals

emailDistribution="rkale@incomm.com "

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

gitRepository="${GIT_REPO}"
gitBranch="${Branch}"
gitCreds="scm-incomm"
// = ("curl https://build.incomm.com//job/Tests/job/tarballMavenartifact/config.xml";


node('linux1'){

	try { 
		cleanWs()

		stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo'
            githubCheckout(gitCreds, gitRepository, gitBranch)
        }
        
        //Reading the YML values
    	stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'test-yamls/dataDrivenCert.yml')
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }
            if (projectProperties.email.emailDistribution != null) {
                emailDistribution = projectProperties.email.emailDistribution
            }

            echo "Sanity Check"
            if (projectProperties.gitInfo.gitYmlFile == null || projectProperties.gitInfo.gitLoc == null ||
             projectProperties.configInfo.logLoc == null || projectProperties.configInfo.configLoc == null ||
             projectProperties.certInfo.certLoc == null || projectProperties.certInfo.certURL == null || 
             projectProperties.certInfo.certBranch == null || projectProperties.certInfo.certFiles == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        }

        stage('Cert Checkouts'){
        	dir('certRepo'){
				githubCheckout(gitCreds, projectProperties.certInfo.certURL, projectProperties.certInfo.certBranch)
			}
        }

		stage('Testing stuff'){
        	certs = projectProperties.certInfo.certFiles.trim()
			certsAll = "${certs}".split(",")
			echo "${certsAll}"
			for (String cert : certsAll) {
				filterCertTrim = cert.trim()
				checkCerts(filterCertTrim)
				}

        }

	} catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}
}

} //end of node

def checkCerts(cert) {
	echo "${cert}"
}

///// The End