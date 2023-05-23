//Lib and var omport setup
import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*
import groovy.json.JsonSlurperClassic

@Library('pipeline-shared-library') _

//@Field projectProperties

//Global Params
//ppiermarini@incomm.com vhari@incomm.com dstovall@incomm.com
emailDistribution = 'rkale@incomm.com ppiermarini@incomm.com vhari@incomm.com dstovall@incomm.com'

ritmNumber = "${RITM}".trim()
ritmNumberAll = "${ritmNumber}".split(",")
currentBuild.result = 'SUCCESS'
current= ""
approvalInformation= ""
//Initiated on the docker node
node('linux') {
    try {
    	//Git checkout  call
    	stage('Status Check') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Invoking API Call'
            echo "${ritmNumberAll}"
            for (String ritms : ritmNumberAll) {
                filterRitmTrim = ritms.trim()
                current = getRITMStatus(filterRitmTrim)
                approvalInformation += current
            }
         
        }
    

    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.result = 'FAILURE'
    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
    notifyBuild(emailDistribution)
	}
} //end of node


def notifyBuild(emailDistribution) {
    emailext attachLog: true, 
        to: emailDistribution,
        subject: "Jenkins: Deploy ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result} \n\nApproval Data for ${ritmNumber}:\n${approvalInformation}\n
        Check console output at ${env.BUILD_URL}\n\n\n"""
}

//Deploy Docker Data Driven

