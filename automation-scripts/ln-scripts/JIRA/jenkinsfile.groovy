import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


//#################
// SNOW-JENKINS-JIRA 
//#################

@Library('pipeline-shared-library') _
//  You should have a development branch, a REST API CALL to connect to remote server,
//  and a Notification Stage to receive an update once the user has been added to the Jira Role.
//Adding Git creds here

gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="origin/development"
gitCreds="scm-incomm"


//Parameters{
		//withCredentials([usernamePassword(credentialsId: 'svc_automation', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
    // some block } 	}
// inputs from build with parameters
//userInput = "${RESTAPICALL}"
//target_env = "${target_env}"
test_suite = "None"
testName = "myTest"

emailDistribution="ppiermarini@incomm.com dstovall@incomm.com rkale@incomm.com"
//General pipeline 

//jiraprojectLoc = "/app/jenkins/workspace/Tests/ApiCallTestJira/automation-scripts/ln-scripts/JIRA"
//shellJiraAPIscript = "jiraTest.sh"

//userInput = InputAction()

devUri = "https://incommdev.service-now.com/api/omm2/jenkins/response"
node('linux'){
	try { 
		cleanWs()

		stage('Testing APICALL'){
			githubCheckout(gitCreds, gitRepository, gitBranch)
			withCredentials([usernamePassword(credentialsId: 'svc_automation', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
				//sh """ /bin/chmod 755 ${jiraprojectLoc}/${shellJiraAPIscript}
					//${jiraprojectLoc}/${shellJiraAPIscript} ${USER} ${PASS}"""
					//sh """curl -u ${USER}:${PASS} https://jira-tst.incomm.com/rest/api/2/project/SCMTEST/roles"""
					//def apiData = sh "curl -D- -u ${USER}:${PASS} -X GET -H 'Content-Type: application/json' https://jira-tst.incomm.com/rest/api/2/project/SCM"
					//def apiData = sh "curl -D- -u ${USER}:${PASS} -X POST --data {"name":"${user_id}"}  -H 'Content-Type: application/json' https://jira-tst.incomm.com/rest/api/2/project/SCM"
				    sh """curl -D- -u ${USER}:${PASS} -X GET -H 'Content-Type: application/json' https://jira-tst.incomm.com/rest/api/2/project?=SCMTEST"""
					
					
					
					// Initializing a local variable
					
					//Check for the boolean condition
					
					//If the condition is false ....
					


					
	 }
	 
		stage('CALL TO SNOW'){
			
					
				
	
			withCredentials([usernamePassword(credentialsId: 'svc_automation', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
				
				// We need to create withcredentials for svc_automation
				sh """curl -d- -u ${USER}:${PASS} -d '{'reqID':'${env.req_id}','userIDField':'${env.user_id}', 'accessServersField':'${env:access_servers}','domainNameField':'${env.domain_name}','ProjectKeyField':'${env:project_key}','AccessTypeField':'${access_type}'}' -X POST -H 'Content-Type: application/json' ${devUri}"""

				// Invoke Rest Method usin devURI		
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

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node

///// The End