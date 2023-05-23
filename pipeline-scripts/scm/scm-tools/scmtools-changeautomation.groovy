import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*
import java.util.ArrayList

@Library('pipeline-shared-library') _

@Field projectProperties

emailDistribution = "ppiermarini@incomm.com svc_jenkins@incomm.com"
userInput="no"
target_env ="no"
userApprove = "no"
orgChgRepository="https://github.com/InComm-Software-Development/scm-tools-change-automation.git"
gitBranch="origin/main"
scmScriptRepository="https://github.com/InComm-Software-Development/scm-change-automation.git"
scmGitBranch="origin/main"
gitCreds="scm-incomm"
credsName = "scm_deployment"
//authToken = "ppiermarini@incomm.com:EZA3tbkTRmGLGTx47ah3529A"
authToken = "svc_wiki@incomm.com:0pLV8J0kHwqVffnBul4fFEF9"
applicationName="JenkinsLTS"


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


artifactList = []


rel=""
now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
nowFormatted.trim()

approvalData = [
        'operators': "[maallen,jdangler,ppiermarini]",
        'adUserOrGroup' : 'maallen,jdangler,ppiermarini',
        'target_env' : "${targetEnv}"
]



currentBuild.result  = 'SUCCESS'

node('linux2'){


    stage('checkout'){
        cleanWs()
		githubCheckout(gitCreds, orgChgRepository, gitBranch)
		dir('changeScripts'){
			githubCheckout(gitCreds, scmScriptRepository, scmGitBranch)
			sh "chmod 755 scripts/*"
			sh "git --version"
		}
    }

    stage('Attachments'){
		
		sshagent([credsName]) {
		sh"""
		##curl -u svc_wiki@incomm.com:0pLV8J0kHwqVffnBul4fFEF9 -o JenkinsLTS_Deployment_Instructions.doc -X GET "https://incomm-payments.atlassian.net/wiki/exportword?pageId=60743450638"
		curl -u svc_wiki@incomm.com:0pLV8J0kHwqVffnBul4fFEF9 -o JenkinsLTS_Testing_Results.doc -X GET "https://incomm-payments.atlassian.net/wiki/exportword?pageId=60743909377"
		"""
		}	
	
	}

    stage('Update Change Details Json') {
		if (pipelineAction == 'Create-Change'){
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
					sleep 2

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

    stage('Create Normal Change'){
		if (pipelineAction == 'Create-Change'){
            withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) {
                sh """
				 ./changeScripts/scripts/createNormalChangeRequest.sh \"${applicationName}\" \"${NEW_VERSION}\" \"${IncommSnowEnv}\" \"${apiKEY}\" \"${MSteamsWebhook}\" \"${Office365Webhook}\"
				"""
            }
		}
    }

    stage('Close Normal Change'){

		if (pipelineAction == 'Close-Change'){
			
            if (fileExists("./applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_response.json")) {
                projectProperties = readJSON file: "applications/${applicationName}/releases/${NEW_VERSION}/create_change_details_response.json"

                echo "please click on the link here to choose a branch"
                CHANGE_STATUS = input message: 'Please choose Change Status ', ok: 'Ok', parameters: [choice(name: 'CHANGE_STATUS', choices: ['', 'successful', 'cancelled','Rolled Back','Partially Completed','Deployed with Issues'], description: 'Change Status?')]

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
					    ./changeScripts/scripts/closeNormalChangeRequest.sh \"${applicationName}\" \"${NEW_VERSION}\" \"${IncommSnowEnv}\" \"${apiKEY}\" \"${MSteamsWebhook}\" \"${CHANGE_STATUS}\" \"${projectProperties.result.u_req_imp_start_date.display_value}\" \"${projectProperties.result.u_req_imp_end_date.display_value}\" \"${Office365Webhook}\"
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

    stage('notification'){
        sendEmailv3(emailDistribution, getBuildUserv1() )
    }

    sendEmailv3
}