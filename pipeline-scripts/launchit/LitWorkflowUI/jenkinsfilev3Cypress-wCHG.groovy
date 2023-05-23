import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk

gitRepository = "https://github.com/InComm-Software-Development/sop-lit-ui.git"
gitCreds = "scm-incomm"
credsName = "scm_deployment"
//// chg automation /////
MSteamsWebhook="https://incomm.webhook.office.com/webhookb2/80aa7eff-d8d7-4f3b-82fc-b8c8c315b0f1@d08e5403-b1c9-4dbf-af91-bab966a84dea/IncomingWebhook/4bd64b60c4fc43e29f5b6161f93baa96/959504b6-a097-44f4-bf13-b855446c3214"
Office365Webhook="https://incomm.webhook.office.com/webhookb2/ff46dd76-0688-4697-b892-f4f060b691eb@d08e5403-b1c9-4dbf-af91-bab966a84dea/IncomingWebhook/05a3fdc3f654464db516dd5a9ab4b043/959504b6-a097-44f4-bf13-b855446c3214"

appReleaseVersion = ""
applicationName = "launchIT-ui"
chgAutomationRepo = "https://github.com/InComm-Software-Development/launchit-change-automation.git"
chgBranch = "main"
IncommSnowEnv = "dev"
////////////////////////

projectProperties =""
testName = "myTest"
automation_env = "automation"

targets = [
	'dev': ['10.42.84.130'],
	'qa': ['10.42.49.209'],
	'uat' :['10.44.0.155'],
	'stg': ['AQSPL02APL2V'],
	'automation' : ['10.42.84.135'],
	'prod1': ['10.40.2.77'],
	'prod2': ['10.40.2.78'],
	'prod3': ['10.40.2.79']
]
// prod servers
//splwfapp02v 10.40.2.78
//splwfapp03v 10.40.2.79
//splwfapp01v 10.40.2.77
emailDistribution="ppiermarini@incomm.com"

//emailDistribution="ppiermarini@incomm.com jkesineni@incomm.com"

artifactDeploymentLoc = "/var/opt/pivotal/pivotal-tc-server-standard/launchworkflow/webapps"
instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/launchworkflow/logs/"
def artifactloc = "${env.WORKSPACE}"

serviceName = "launchworkflow"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'launchIT.ui'
artifactId = 'launchIT-ui'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'launchit.war'
artifactExploded = "launchit"


userApprove = "Welcome to the new Jenkinsfile"
relVersion = "null"

user = "tcserver"
group = "pivotal"


node('npm'){

currentBuild.result = "SUCCESS"
jdk=tool name:'openjdk-11.0.7.10-0'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"
node="/app/jenkins/.nvm/versions/node/v14.17.1/bin/node"

echo "Build_Type = ${pipelineAction}"
sh "whoami"

	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			//cleanWs()
			//listGithubBranches(gitCreds,gitRepository)
			//echo "Checking out p${env.BRANCH_SCOPE}p"
			//gitBranch = "${env.BRANCH_SCOPE}"
			cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		stage('Build'){

			if (pipelineAction == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com  or sonar
				//sh "${maven} compile install -X"
				echo "DEBUG 1"
				sh "${node} --version"
				sh "nvm use 14.17.1"
				sh "${node} --version"
				sh "source ~/.nvm/nvm.sh"
				sleep(5)
				withSonarQubeEnv('sonarqube.incomm.com'){
					sh "${maven} clean deploy -X sonar:sonar" //-Dsonar.branch.name=${gitBranch}
				}
				sleep(3)
				echo "DEBUG 2"
				//sh "${maven} deploy"
				sh "cp target/${artifactId}*.${artExtension} ${artifactName}"

			} else if (pipelineAction == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."

				withCredentials([usernamePassword(credentialsId: 'scm-incomm', passwordVariable: 'gitPass', usernameVariable: 'gitUser')]) {

					sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
					sleep(3)
					sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
					
				}
				
			} else {
				echo "pipelineAction is ${pipelineAction}"
			}

		} //stage

		stage('SonarQube Quality Gate Check') {
			if (pipelineAction == 'Build'){
					
				sleep(30) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
				
				timeout(time: 3, unit: 'MINUTES') {	
					def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
						if (qg.status != 'OK') {
						error "Pipeline aborted due to quality gate failure: ${qg.status}"
						}
				echo "after QG timeout"
				}
					
			} else {
					echo "Quality Gate not needed for Release or Promote"
			}
		}//end QualityGate


		//select the artifact 
		stage('Get Artifact'){

			if (pipelineAction == 'Promote') {
				
			artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
			sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"
			
			//	artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

			} else {
				echo "not getting artifact during ${pipelineAction}"
			}

		}

		stage('Deployment'){
			// by default promtes to DEV environment
			if (pipelineAction == 'Promote'){

				echo "Deploying to ${target_env}"
				deployComponents(target_env, targets[target_env], "${artifactName}")

			} else if ((pipelineAction == 'Build') && (runCypress == 'true')) {
				
				echo "Deploying to automation server for Cypress test suite ${suiteName}"
				deploy_Automation(automation_env, targets[automation_env], "${artifactName}")
				runCypress(suiteName)
				target_env = "dev"
				echo ""
				echo "Deploying to ${target_env}"
				echo ""
				deployComponents(target_env, targets[target_env], "${artifactName}")
			} else {
				if (pipelineAction == 'Promote'){
				//target_env = "dev"
				//echo "Deploying to ${target_env}"
				//deployComponents(target_env, targets[target_env], "${artifactName}")
				echo "default deploy action ${pipelineAction}"
				}
			}
		}

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
						
					}
						
				} // dir change_automation
					
			} else { //top if
				echo "Other half ${pipelineAction}"
				catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                sh "exit 1"
				}
			}

		}


	} catch (exc) {

			currentBuild.result = 'FAILURE'
			//sendEmail(emailDistribution, gitBranch, pipelineAction, target_env, userApprove)
			echo 'ERROR:  ' + exc.toString()
			throw exc

	} finally {

		stage('Notification'){
			
		sendEmail(emailDistribution, gitBranch, pipelineAction, target_env, userApprove)	
		}
	}

} //end of node

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def runCypress(suiteName){
	
	echo "DEBUG runCypress ${suiteName}"
	sshagent([credsName]) {
		sh"""
			#npx cypress run
			npm install
			#npx cypress run --env SUITE=${suiteName} --record --key ae3a0274-4599-4e47-bb31-3254b5eba3c3
			npx cypress run server=qa --spec "cypress/integration/test_flows/regular-cases/happy-path.spec.js" --record --key e0962e78-2261-4902-86f1-ed83ca078229
		"""
	}
}

def deployComponents(envName, targets, Artifact){

	echo "my env= ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { deploy(it, artifactDeploymentLoc, Artifact) }]
	}
	parallel stepsInParallel

}

def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
			echo "debug 1"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
			echo "debug 2"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${artifactDeploymentLoc}/${artifactName}'
			echo "debug 3"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactExploded}'
			echo "debug 4"
			scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:$artifactDeploymentLoc/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}

}


def deploy_Automation(envName, targets, Artifact){

	echo "my env= ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { deployautomation(it, artifactDeploymentLoc, Artifact) }]
	}
	parallel stepsInParallel

}

def deployautomation(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
			echo "debug 1"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
			echo "debug 2"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${artifactDeploymentLoc}/${artifactName}'
			echo "debug 3"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactExploded}'
			echo "debug 4"
			scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:$artifactDeploymentLoc/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}

}


def getFromArtifactory(){
	if (userInput == "Promote") {
		echo "Select an artifact from Artifacory"

		relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)

		echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"
	} else {
		echo "not promoting-Skipping"
	}
}

