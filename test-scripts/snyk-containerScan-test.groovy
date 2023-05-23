import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// Globals
emailDistribution = "jrivett@incomm.com"
imageName = "rtg-altpayolsconnector"
snykTokenId = "snyk-rtg"
imageTag = "1.0.1-k8s"

node('docker'){
    try {  

        stage('snyk-container-scan') {
			snykContainerScanTest(snykTokenId, imageName, imageTag)
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