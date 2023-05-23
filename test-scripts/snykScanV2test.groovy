import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
gitBranch = "${GIT_BRANCH}"

// Globals
emailDistribution = "jrivett@incomm.com"
gitCreds = "scm-incomm"
gitRepository = "https://github.com/InComm-Software-Development/ddc-ui.git"

snykCredsId = 'snyk-poc'

node('linux'){
    try {  

        stage('checkout') {
            githubCheckout(gitCreds, gitRepository, gitBranch)
        }

        stage('setvars') {
            pom = readMavenPom file: 'pom.xml'
            artifactId = pom.getArtifactId();
			artifactVersion = pom.getVersion();
        }

        stage('scan') {
            snykScanV2(snykCredsId, artifactId, artifactVersion)
        }


    }

    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailv3(emailDistribution, getBuildUserv1())	
	}
}
