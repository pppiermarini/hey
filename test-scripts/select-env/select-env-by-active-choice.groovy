import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
targetServer = "${ENV_ID}"
targetAddresses = "${TARGET_ADDRESSES}"


// Pipeline Globals
emailDistribution = "vhari@incomm.com, dstovall@incomm.com, ppiermarini@incomm.com, rkale@incomm.com, jrivett@incomm.com"



node('linux'){
    try {  
        stage('display-selected-target') {
            println("${targetServer}'s targets: ${targetAddresses}")
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
