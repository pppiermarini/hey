import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// GLOBALS/UI PARAMS
credsName = "scm_deployment"
gitCredentials = "scm-incomm"
chmod = "755"
targetsAll = null
pipelineAction = "${PIPELINE_ACTION}"  // input from job or yml for deploy or chnge request etc...
targetEnv = "${TARGET_ENV}"
ymlRepository = "${YML_REPOSITORY}" // NOT THE DEV'S CODE, this is where DD Info is stored
ymlBranch = "${YML_BRANCH}"
configTag = "${CONFIG_VERSION}"

emailDistribution = "jrivett@incomm.com"

// YML VALUE PLACEHOLDERS
// ARTIFACT INFO
repoId = ""
groupId = ""
artifactId = ""
artExtension = ""
artifactName = ""

// DEPLOYMENT INFO
serviceName = ""
artifactDeploymentLoc = ""
instanceLogsLoc = ""
configFolderLoc = ""
user = ""
group = ""
warFolderName = ""
newWarFolderName = ""
gitDeployModule = ""
configRepository = ""
deployCommonConfig = null

// APPROVAL DATA
approvalData = [
    'operators': "[glindsey,kroopchand]",
    'adUserOrGroup' : 'glindsey,kroopchand',
    'target_env' : "${targetEnv}"
]

// TARGETS
targets_mdm_lle = [
    'dev':  ['10.42.17.67','10.42.17.64'],
    'qa':   ['10.42.80.26','10.42.80.27'],
    'int1': ['10.42.32.205'],
    'int2': ['10.42.32.206'],
    'int': ['10.42.32.205','10.42.32.206'],
]

targets_mdm_api_svc = [
	'PROD_A_1_2': ['10.40.5.105','10.40.5.114'],
	'PROD_A_3_4': ['10.40.5.115','10.40.5.116'],
	'PROD_A_5_6': ['10.40.5.117','10.40.5.118'],
	'PROD_B_8_9_10': ['10.40.7.63','10.40.7.75','10.40.7.76'],
	'PROD_B_11_12_13': ['10.40.7.77','10.40.7.78','10.40.7.79']
]

// TODO: GET HLE TARGETS FOR WEBAPP UI

node('linux2'){
    // PIPELINE STARTS HERE
    try {
        // get approval if high-level environment
        stage('Approval Check') {
            if (targetEnv == "uat" || targetEnv == "pre-prod" ||targetEnv == "PROD_A_1_2" || 
				targetEnv == "PROD_A_3_4" || targetEnv == "PROD_A_5_6" || targetEnv == "PROD_B_8_9_10" || 
				targetEnv == "PROD_B_11_12_13") {
                	getApproval(approvalData)
            }
        }

    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo containing the YML'
            githubCheckout(gitCredentials, ymlRepository, ymlBranch)

        }

        // determines which targets to use based on results of defineTargets()
        stage('Define Targets') {
            defineTargets()
            echo "Target Env: ${targetEnv}: All Targets:"
            println  targetsAll[targetEnv]
        }

        // Reading data-driven pipeline values from YML file
    	stage('Read YAML file') {
            echo 'Reading dataDriven.yml file'
            projectProperties = readYaml (file: 'mdmDataDriven.yml')
            if (projectProperties == null) {
                throw new Exception("dataDriven.yml not found in the project files.")
            }

            // TODO: Ensure the values in this sanity check match the values in YML
            echo "Sanity Check"
            if (projectProperties.artifactInfo == null || projectProperties.deployment == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        }

		stage('Checkout Config') {
			configRepository = projectProperties.deployment.configRepository

			if ("${DEPLOY_CONFIG}") {
				dir('configs') {
					githubCheckout(gitCredentials, configRepository, configTag)
				}
			}
		}

        // Get artifact from artifactory
        stage('Get Artifact') {
            // Assign relevant values from YML
            repoId = projectProperties.artifactInfo.repoId
            groupId = projectProperties.artifactInfo.groupId 
            artifactId = projectProperties.artifactInfo.artifactId 
            artExtension = projectProperties.artifactInfo.artExtension 
			artifactName = projectProperties.artifactInfo.artifactName
            
			// Select and download artifact
            list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
            echo "the list contents ${list}"
            artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
            parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
            sleep(3)
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
            
			// Renames artifact to generic file name
            echo "the artifact ${artifactId}-${artifactVersion}.${artExtension}"
            sh "cp -rp ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"
            sh "ls -ltr" 
		}

        stage("Deployment to ${targetEnv}"){
			// Assign relevant values from YML
			serviceName = projectProperties.deployment.serviceName
			artifactDeploymentLoc = projectProperties.deployment.artifactDeploymentLoc
			configFolderLoc = projectProperties.deployment.configFolderLoc
			instanceLogsLoc = projectProperties.deployment.instanceLogsLoc
			user = projectProperties.deployment.user
			group = projectProperties.deployment.group
			warFolderName = projectProperties.deployment.warFolderName
			newWarFolderName = projectProperties.deployment.newWarFolderName
			gitDeployModule = projectProperties.deployment.gitDeployModule
			deployCommonConfig = projectProperties.deployment.deployCommonConfig

			deployComponents(targetEnv, targetsAll[targetEnv], "${artifactName}")
		}
		
        // TODO: YAMLIZE INPUT FOR CHG AUTOMATION STAGES
		stage('Change Request'){

			if (pipelineAction == 'ChangeRequest') {
				
				pom = readMavenPom file: 'pom.xml'
				appReleaseVersion = pom.getVersion();
				
				dir('change_automation'){
					githubCheckout(gitCreds,chgAutomationRepo,chgBranch)
					//sh """ 
					//git rev-parse --short HEAD >> gitshort.txt
					//"""
					//def id = readFile 'gitshort.txt'  vganjgavkar
					//echo "tom ${id}"
					//pom = readMavenPom file: 'pom.xml'
					//def pver = pom.getVersion();
					//rel ="${pver}-${id}"
					//echo "${rel}"   //automated appReleaseVersion result
					sleep(2)
				
					if (fileExists("./applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_response.json")) {
						echo 'Yes'
						echo "This Release has been run or there is a problem.."
						echo "the change request details response json already exists"
						
						catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
						sh "exit 1"
						}
						
					} else {
						echo 'No'
						
						sshagent([credsName]) {
						sh"""
							pwd
							jq '.u_req_imp_start_date="${u_req_imp_start_date}" | \
							.u_req_imp_end_date="${u_req_imp_end_date}" | \
							.u_change_author_manager="${u_change_author_manager}" | \
							.assigned_to="${assigned_to}" | .u_employee_pir_tester="${u_employee_pir_tester}" | \
							.u_peer_reviewer="${u_peer_reviewer}" | .u_no_itoc="${u_no_itoc}"' \
							applications/${applicationName}/create_change_request_template.json > ${appReleaseVersion}.json
							
							mv ${appReleaseVersion}.json applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json
							#git add applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json
							#git commit -m"commit change details json"
							#git push origin main
							ls -ltr applications/${applicationName}/releases/${appReleaseVersion}
							sleep 5
						"""
						}
						echo "Read the create_change_details_request.json"
						projectProperties = readJSON file: "applications/${applicationName}/releases/${appReleaseVersion}/create_change_details_request.json"

						echo "${projectProperties.u_req_imp_start_date}"
						//echo "${projectProperties.u_sub_category}"
						
						withCredentials([string(credentialsId: 'chg_automation', variable: "apiKEY")]) {
							sh """
							./scripts/createNormalChangeRequest.sh "${applicationName}" "${appReleaseVersion}" "${IncommSnowEnv}" "${apiKEY}" "${MSteamsWebhook}" "${Office365Webhook}"
							"""
						}
						
					} // dir change_automation
						
				}
					
			} 
			else { //top if
				echo "No Change request when pipelineAction is: ${pipelineAction}"
			}

		} //change request

		// TODO: YAMLIZE INPUT FOR CHG AUTOMATION STAGES
		stage('Close Normal Change') {
			// when {
			// 	expression { (params.pipelineAction == "CloseChangeRequest")}
			// }
			
			if (pipelineAction == 'CloseChangeRequest') {
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
				} //dir

			}
			else { //top if
				echo "No Change request when pipelineAction is: ${pipelineAction}"
			}

		}	//close normal change stage	
		
		
    }//try

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

def defineTargets() {
    println("${JOB_NAME}")

	// check if LLE and use default envs if so
	if (!target_env.matches(".*prod.*|.*PROD.*")) {
		targetsAll = targets_mdm_lle
		return
	}
    
    parentFolder = "${JOB_NAME}".split("/")[0].toLowerCase()
    println(parentFolder)

    if (parentFolder ==~ /mdm-api-svc-data-driven/) {
        targetsAll = targets_mdm_api_svc
    }

    else {
        throw new Exception("No Application Targets found for the defined application")
    }

}

def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	//echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		// Stop Service
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogsLoc}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
		"""

		// Always removes old artifact and copies the new one
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${warFolderName}*'
			scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/${newWarFolderName}.${artExtension}
		"""
		
		// Deploys configuration if DEPLOY_CONFIG is true
		if (("${DEPLOY_CONFIG}")) {
			// Deploy common config iff 'deployCommonConfig' is true
			if (("${deployCommonConfig}")) {
				sh """
					scp -o StrictHostKeyChecking=no -r configs/common/${gitDeployModule}/* root@${target_hostname}:${configFolderLoc}
				"""
			}

			// Always deploy env-dependent config
			sh """
				scp -o StrictHostKeyChecking=no -r configs/${envName}/${gitDeployModule}/* root@${target_hostname}:${configFolderLoc}
			"""
		}

		// CHMOD and Start service
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/*'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${configFolderLoc}/*'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/*'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${configFolderLoc}/*'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
			echo 'Restart service:  ${serviceName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}

}