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

emailDistribution = "ppiermarini@incomm.com"

appRepository="https://github.com/InComm-Software-Development/hello-world.git"
appBranch="origin/master"
//change automation repo
gitRepository="https://github.com/InComm-Software-Development/scm-change-automation.git"
gitBranch="origin/main"
gitCreds="scm-incomm"
credsName = "scm_deployment"

MSteamsWebhook="https://incomm.webhook.office.com/webhookb2/80aa7eff-d8d7-4f3b-82fc-b8c8c315b0f1@d08e5403-b1c9-4dbf-af91-bab966a84dea/IncomingWebhook/4bd64b60c4fc43e29f5b6161f93baa96/959504b6-a097-44f4-bf13-b855446c3214"
Office365Webhook="https://incomm.webhook.office.com/webhookb2/ff46dd76-0688-4697-b892-f4f060b691eb@d08e5403-b1c9-4dbf-af91-bab966a84dea/IncomingWebhook/05a3fdc3f654464db516dd5a9ab4b043/959504b6-a097-44f4-bf13-b855446c3214"

applicationName="hello-world"
IncommSnowEnv="dev"  // this is for testing.

rel=""
now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
nowFormatted.trim()

approvalData = [
	'operators': "[jkesineni,ppiermarini]",
	'adUserOrGroup' : 'jkesineni,ppiermarini',
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

	stages {

		stage('Initialize') {
			steps {
				cleanWs()
				githubCheckout(gitCreds,appRepository,appBranch)
				dir('change_automation'){
					githubCheckout(gitCreds,gitRepository,gitBranch)
					sh "chmod 755 scripts/*"
				}
			}
		}


		stage('Build') {
			when {
				expression { (params.pipelineAction == "Build") }
			}
			
			steps {
				echo "Maven Build ${params.appReleaseVersion}"
			}

		}

		
		stage('Deploy') {
			when {
				expression { (params.pipelineAction == "Deploy") && (params.targetEnv != "prod") }
			}
			
			steps {
				echo "Deploying to ${params.targetEnv}"

			}
		}
		
		// Potential option to creat the release string folder and populate with documents
		stage('Build Release String') {
			when {
				expression { (params.RELEASE_STRING == true) }
			}
			
			steps {

				script{
					dir('appdir'){
						githubCheckout(gitCreds,appRepository,appBranch)
						sh """ 
						git rev-parse --short HEAD >> gitshort.txt
						"""
						def id = readFile 'gitshort.txt'
						echo "tom ${id}"
						pom = readMavenPom file: 'pom.xml'
						def pver = pom.getVersion();
						rel ="${pver}-${id}"
						echo "${rel}"   //automated appReleaseVersion result
						echo "paul"
					}
				echo "${rel}-${nowFormatted}"
				}
				
			}
		}
		
		stage('Approve Change Automation'){
			when {
				expression { (params.pipelineAction == "ChangeRequest") }
			}
			
			steps {

				script{
        		approveResult = getApproval(approvalData)
				echo "Approval Result: ${approveResult}"
					}

				}
				
			}

		// creates the release change details based on template.
		stage('Create Change Details Json') {
			when {
				expression { (params.pipelineAction == "ChangeRequest") }
			}

			steps {

				script{
				dir('change_automation'){
					echo "appReleaseVersion = ${appReleaseVersion}"
					
					if (fileExists("./applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_response.json")) {
						echo 'Yes'
						echo "This Release has been run or there is a problem.."
						echo "the change request details json already exists"
						
						catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
						sh "exit 1"
						}
						
					} else {
						echo 'No'
						sshagent([credsName]) {
						sh"""
							jq '.u_req_imp_start_date="${u_req_imp_start_date}" | \
							.u_req_imp_end_date="${u_req_imp_end_date}" | \
							.u_change_author_manager="${u_change_author_manager}" | \
							.assigned_to="${assigned_to}" | .u_employee_pir_tester="${u_employee_pir_tester}" | \
							.u_peer_reviewer="${u_peer_reviewer}" | .u_no_itoc="${u_no_itoc}"' \
							applications/${applicationName}/create_change_request_template.json > ${appReleaseVersion}.json
							
							mv ${appReleaseVersion}.json applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json
							git add applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json
							git commit -m"commit change details json"
							git push origin main
							sleep 3

						"""
						}
						echo "Read the json"
						projectProperties = readJSON file: "applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json"

						echo "${projectProperties.u_req_imp_start_date}"
						//echo "${projectProperties.u_sub_category}"
					}

					}
				}
			}
		}

		stage('Create Normal Change') {
			when {
				expression { (params.pipelineAction == "ChangeRequest")}
			}
			
			steps {
				script{
					dir('change_automation'){
						//projectProperties = readJSON file: "applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json"
						//echo "${projectProperties.u_env_to_chg}"
						//echo "${projectProperties.u_req_imp_start_date}"
						//echo "${projectProperties.u_sub_category}"

						echo "CREATING CHANGE"
						
						withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) {
							sh """
							./scripts/createNormalChangeRequest.sh \"${applicationName}\" \"${appReleaseVersion}\" \"${IncommSnowEnv}\" \"${apiKEY}\" \"${MSteamsWebhook}\" \"${Office365Webhook}\"
							"""
						}
					}
				}
			}
		}
		
		stage('Close Normal Change') {
			when {
				expression { (params.pipelineAction == "CloseChangeRequest")}
			}
			
			steps {
				script{
					dir('change_automation'){
					if (fileExists("./applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_response.json")) {
						projectProperties = readJSON file: "applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_response.json"

						echo "please click on the link here to choose a branch"
						CHANGE_STATUS = input message: 'Please choose Change Status ', ok: 'Ok', parameters: [choice(name: 'CHANGE_STATUS', choices: ['', 'successful', 'cancelled'], description: 'Change Status?')]
						
						echo "--------------------------"
						echo "Close Change Request"
						echo "p${applicationName}p"
						echo "p${appReleaseVersion}p"
						echo "p${CHANGE_STATUS}p"
						echo "p${projectProperties.result.u_req_imp_start_date.display_value}p"
						echo "p${projectProperties.result.u_req_imp_end_date.display_value}p"
						echo "p${u_req_imp_start_date}p"
						echo "p${u_req_imp_end_date}p"
						echo "--------------------------"
						
						withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) {
						sh """
							./scripts/closeNormalChangeRequest.sh \"${applicationName}\" \"${appReleaseVersion}\" \"${IncommSnowEnv}\" \"${apiKEY}\" \"${MSteamsWebhook}\" \"${CHANGE_STATUS}\" \"${projectProperties.result.u_req_imp_start_date.display_value}\" \"${projectProperties.result.u_req_imp_end_date.display_value}\" \"${Office365Webhook}\"
						"""
						}

					} else {
						echo 'NO'
						echo "The response file does not exist so can not close this change"
						echo "or there is a problem"
						
						catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
						sh "exit 1"
						}
					}
					}
				}
			}
		}
		
	}//stages
	
	
}	



