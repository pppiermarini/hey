import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

@Field projectProperties
NOTIFICATION_LIST = "ppiermarini@incomm.com"
//emailDistribution = "ppiermarini@incomm.com svc_jenkins@incomm.com"

orgChgRepository="https://github.com/InComm-Software-Development/scm-tools-change-automation.git"
gitBranch="origin/main"
scmScriptRepository="https://github.com/InComm-Software-Development/scm-change-automation.git"
scmGitBranch="origin/main"
gitCreds="scm-incomm"
credsName = "scm_deployment"

//authToken = "ppiermarini@incomm.com:EZA3tbkTRmGLGTx47ah3529A"
authToken = "svc_wiki@incomm.com:0pLV8J0kHwqVffnBul4fFEF9"
applicationName="hello-world"
ticker =""

targetEnv = "dev"
 
MSteamsWebhook="https://incomm.webhook.office.com/webhookb2/80aa7eff-d8d7-4f3b-82fc-b8c8c315b0f1@d08e5403-b1c9-4dbf-af91-bab966a84dea/IncomingWebhook/4bd64b60c4fc43e29f5b6161f93baa96/959504b6-a097-44f4-bf13-b855446c3214"
Office365Webhook="https://incomm.webhook.office.com/webhookb2/ff46dd76-0688-4697-b892-f4f060b691eb@d08e5403-b1c9-4dbf-af91-bab966a84dea/IncomingWebhook/05a3fdc3f654464db516dd5a9ab4b043/959504b6-a097-44f4-bf13-b855446c3214"

UAT_SIGNOFF_URL =""
REGRESSION_TESTS_URL =""

IncommSnowEnv="dev"  // this is for testing.

// move these 3 as inputs from jenkins job if needed
QA_SIGNOFF_URL = ""
UAT_SIGNOFF_URL = ""
REGRESSION_TESTS_URL = ""

script_hostname = "10.40.6.230"

//target_env = "${target_env}"

targets = [
	'dev': ['10.40.6.201'],
	'qa' : ['10.40.6.201'],
	'uat' : ['10.44.0.x'],
	'automation' : ['10.42.84.x'],
	'prod' : ['10.40.6.201']
]

artifactList = []

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'hello-world-testing'
artifactId = 'hello-world'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = ''

artifactDeploymentLoc = "/opt/hello-world"
instanceLogs = "/opt/hello-world/logs"
serviceName = "hello-world.service"

user = "tcserver"
group = "pivotal"

result = ""
CHANGE_STATUS = ""

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
nowFormatted.trim()

approvalData = [
        'operators': "[maallen,jdangler,ppiermarini]",
        'adUserOrGroup' : 'maallen,jdangler,ppiermarini',
        'target_env' : "${targetEnv}"
]



currentBuild.result  = 'SUCCESS'

pipeline {
	agent {
		label "linux2"
	}


	options {
		disableConcurrentBuilds()
	}

	tools {
		maven 'maven321'
		jdk 'openjdk-11.0.5.10'
	}
	
stages{
	    stage('params'){
			steps{
				script{
				echo "pipe Action p${pipelineAction}p"
				echo "version  ${NEW_VERSION}"
				echo "target  ${target_env}"
				}
			}
		}
	
    stage('checkout'){
		when {
			expression { (params.pipelineAction == "Create-Change") }
		}
		steps{
        cleanWs()
			script {
				githubCheckout(gitCreds, orgChgRepository, gitBranch)
				dir('changeScripts'){
					githubCheckout(gitCreds, scmScriptRepository, scmGitBranch)
					sh "chmod 755 scripts/*"
					sh "git --version"
				}
			}
		}
    }

    stage('Attachments'){
		when {
			expression { (params.pipelineAction == "Create-Change") }
		}
		steps{
			
			script{
				sshagent([credsName]) {
				sh"""
				##curl -u svc_wiki@incomm.com:0pLV8J0kHwqVffnBul4fFEF9 -o JenkinsLTS_Deployment_Instructions.doc -X GET "https://incomm-payments.atlassian.net/wiki/exportword?pageId=60743450638"
				curl -u svc_wiki@incomm.com:0pLV8J0kHwqVffnBul4fFEF9 -o JenkinsLTS_Testing_Results.doc -X GET "https://incomm-payments.atlassian.net/wiki/exportword?pageId=60743909377"
				"""
				}
			}
		}
	
	}

    stage('Update Change Details Json') {
		when {
			expression { (params.pipelineAction == "Create-Change") }
		}		
		steps{
			
			script{
				echo "NEW_VERSION = ${NEW_VERSION}"

				if (fileExists("./applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_response.json")) {
					echo 'Yes'
					echo "This Release has been run or there is a problem.."
					echo "the change request details json already exists"

					catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
						sh "exit 1"
					}

				} else {
					withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) { // authentication for Snow
						sh """
						 ./changeScripts/scripts/createNormalChangeRequestDetailsFile.sh \"${applicationName}\" \"${CURRENT_VERSION}\" \"${NEW_VERSION}\" \"${u_req_imp_start_date}\" \"${u_req_imp_end_date}\" \"${RELEASE_NOTES_URL}\" \"${QA_SIGNOFF_URL}\" \"${UPGRADE_PLUGINS_URL}\" \"${UPGRADE_JENKINS_URL}\" \"${authToken}\"
						"""
				}
				sleep(2)
				sshagent([credsName]) {
					sh"""
						ls -ltr
						ls -ltr applications/${applicationName}/releases/
						pwd
						jq '.assigned_to="${assigned_to}" | .u_employee_pir_tester="${u_employee_pir_tester}" | \
						.u_peer_reviewer="${u_peer_reviewer}" | .u_no_itoc="${u_no_itoc}"' \
						create_change_details_request.json > ${NEW_VERSION}.json

						sed -i -e 's/CURRENT_VERSION/${CURRENT_VERSION}/g' ${NEW_VERSION}.json
						sed -i -e 's/NEW_VERSION/${NEW_VERSION}/g' ${NEW_VERSION}.json
						sed -i -e 's,DEPLOYMENT_PLAN_URL,${DEPLOYMENT_PLAN_URL},g' ${NEW_VERSION}.json
						sleep 2
						mv *.doc applications/${applicationName}/releases/${NEW_VERSION}/ 2>/dev/null
						mv ${NEW_VERSION}.json applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_request.json
						#cleanup intermediate file
						rm -f create_change_details_request.json
						
						git add applications/.
						git commit -m"commit change documents to release folder"
						git push origin main

					"""
				}
					echo "Read the json"
					projectProperties = readJSON file: "applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_request.json"

					echo "${projectProperties.u_req_imp_start_date}"
					echo "description        ${projectProperties.description}"
					echo "short_description  ${projectProperties.short_description}"
					echo "test_plan          ${projectProperties.test_plan}"
				}
			}
		}

    }


    stage('Create Normal Change'){
		when {
			expression { (params.pipelineAction == "Create-Change") }
		}
		steps{
			script{
				echo "Creating Normal Change...."
				withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) {
					sh """
					 ./changeScripts/scripts/createNormalChangeRequest.sh \"${applicationName}\" \"${NEW_VERSION}\" \"${IncommSnowEnv}\" \"${apiKEY}\" \"${MSteamsWebhook}\" \"${Office365Webhook}\"
					"""
				}
			}
		}
    }
	
	
    stage('Schedule the Deployment'){
		when {
			expression { (params.pipelineAction == "Create-Change") }
		}
		steps{
			script{
				
				String sDate = "${projectProperties.u_req_imp_start_date}"
				start_date_s = sh(script: """(date -d '${sDate}' \\+'%s')""", returnStdout: true).trim() as Integer
				curDate_s = sh(script: """(date +'%s')""", returnStdout: true).trim() as Integer
				
				echo "Start ${start_date_s}"
				echo "Cur   ${curDate_s}"
				echo "Start date in seconds: ${start_date_s}, Currnet Date in seconds: ${curDate_s}"
	 
				ticker = (start_date_s - curDate_s ) // seconds to wait
				//ticker = (start_date_s - curDate_s ) / 60 //Converting to mins

				echo "Time difference in minutes: ${ticker}"

				sshagent([credsName]) {
					sh """
						ssh -q -o StrictHostKeyChecking=no root@${script_hostname} 'sh -c "( ( /home/ppiermarini/launchJob.sh 120 ${NEW_VERSION} Deploy-Change ${target_env} &>/dev/null ) & )"'
					"""
				}
			
            }
		}
	}


    stage('Cancel Scheduled Deployment'){
		//
		//	TBD  Need to figure out how to handle multiple schedules
		//
		//
		when {
			expression { (params.pipelineAction == "Cancel-Change") }
		}
		steps{
			script{
				sshagent([credsName]) {
					sh """
					echo "Cancelling the change"
						ssh -q -o StrictHostKeyChecking=no root@${script_hostname} 'sh -c "( ( /home/ppiermarini/stopChange.sh &>/dev/null ) & )"'
					"""
				}
			}
		}
    }
	
		//select the artifact 
	stage('Get Artifact'){

	when {
		expression { (params.pipelineAction == "Deploy-Change") }
	}
		steps{
			script{
				echo "Read the json"
				projectProperties = readJSON file: "applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_request.json"

				echo ""
				echo "Deployment version ${NEW_VERSION}"
				echo ""
				artifactWget(repoId, groupId, artifactId, artExtension, NEW_VERSION)
			}
		}

	}


	stage('Deployment'){
	when {
		expression { (params.pipelineAction == "Deploy-Change") }
	}
	
		steps{
			script{

				deployComponents(target_env, targets[target_env], "${artifactName}")

			}
		}

	}


	stage('Automated Testing'){
	when {
		expression { (params.pipelineAction == "Deploy-Change") }
	}
	
		steps{
			script{
				echo "runTests"
				result = runTest(target_env, targets[target_env], "${artifactName}")

				echo "Testing Result: ${result}"

				String op = result.toString()
				result = op.replaceAll("[\\[\\](){}]","");
      			def arr_result = result.split(":")
				echo "Server ${arr_result[0]}"
				echo "Test Result ${arr_result[1]}"


			def test_result = "SUCCESS"
				if (arr_result[1] == "SUCCESS"){
					echo "Testing completed: ${arr_result[1]}"
					//
					//  TBD
					//  Close Change
					CHANGE_STATUS = "successful"
					pipelineAction = "Close-Change"
				} else {
					echo "Testing completed: ${arr_result[1]}"
					CHANGE_STATUS = "failed"
					echo "ROLLING BACK"
					pipelineAction = "Close-Change"
				}

			}
		}

	}


    stage('Close Normal Change'){
	when {
		expression { (pipelineAction == "Close-Change") }
	}
		steps{
			
			script{
				
				if (fileExists("./applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_response.json")) {
					projectProperties = readJSON file: "applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_response.json"

					echo "please click on the link here to choose a branch"
					//CHANGE_STATUS = input message: 'Please choose Change Status ', ok: 'Ok', parameters: [choice(name: 'CHANGE_STATUS', choices: ['', 'successful', 'cancelled','Rolled Back','Partially Completed','Deployed with Issues'], description: 'Change Status?')]

					echo "--------------------------"
					echo "Close Change Request"
					echo "${applicationName}"
					echo "${NEW_VERSION}"
					echo "${CHANGE_STATUS}"
					echo "${projectProperties.result.u_req_imp_start_date.display_value}"
					echo "${projectProperties.result.u_req_imp_end_date.display_value}"
					echo "${u_req_imp_start_date}"
					echo "${u_req_imp_end_date}"
					echo "--------------------------"

					withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) {
						sh """
						    #./changeScripts/scripts/closeNormalChangeRequest.sh \"${applicationName}\" \"${NEW_VERSION}\" \"${IncommSnowEnv}\" \"${apiKEY}\" \"${MSteamsWebhook}\" \"${CHANGE_STATUS}\" \"${projectProperties.result.u_req_imp_start_date.display_value}\" \"${projectProperties.result.u_req_imp_end_date.display_value}\" \"${Office365Webhook}\"
							./changeScripts/scripts/closeNormalChangeRequest.sh "${applicationName}" "${NEW_VERSION}" "${IncommSnowEnv}" "${apiKEY}" "${MSteamsWebhook}" "${CHANGE_STATUS}" "${projectProperties.result.u_req_imp_start_date.display_value}" "${projectProperties.result.u_req_imp_end_date.display_value}" "${Office365Webhook}"
						"""
					}

				} else {

					echo "The response file does not exist so can not close this change"
					echo "or there is a problem"

					catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
						sh "exit 1"
					}
				}
			}
		}

    }

 //   stage('notification'){
//		steps{
//			script{
//				sendEmailv3(emailDistribution, getBuildUserv1() )
//			}
//		}
//   }
	
	stage('notification') {
		steps {
			echo """
				
********************************************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
--------------------------------------------------------------------------------
"""

		script {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that deployment has taken place"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """

Automated change end to end status

**************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
**************************************************\n\n\n
"""
				}
			}
		}
	}

} //stages

} //pipeline

/////// below this line be dragons /////


def deployComponents(envName, targets, Artifact){

	echo "my env= ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { deploy(it, artifactDeploymentLoc, Artifact) }]
	}
	parallel stepsInParallel

}


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"

	echo "Deploy"
	sleep(5)
	echo "Deploy"
	sleep(5)
	sshagent([credsName]) {
		sh """
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f $artifactDeploymentLoc/${artifactName}'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv $artifactDeploymentLoc/${artifactId}'
			#scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:$artifactDeploymentLoc/
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactId}*.${artExtension}'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}

}


def runTest(envName, targets, Artifact){

	echo "my env= ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { Test(it, artifactDeploymentLoc, Artifact) }]
	}
	parallel stepsInParallel

}


def Test(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"
	echo "Testing..."
	sleep(3)
	echo "Testing..."
	sleep(3)
	echo "Testing..."
	sleep(3)
	echo "More Testing... Done"

	sshagent([credsName]) {
		sh """
			pwd
			ls -ltr
			# TBD  Execute tests
		"""
	}
	def res = "SUCCESS"
	echo "res equals ${res}"
	
return res;
}

