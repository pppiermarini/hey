import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk

gitRepository = "https://github.com/InComm-Software-Development/sop-lit-ui.git"
gitBranch = "${BRANCH}"
gitCreds = "scm-incomm"
credsName = "scm_deployment"

Artifactory rest api
curl -u ppiermarini:cmVmdGtuOjAxOjE2OTY2OTAwMzI6bnJ5OThkYXozdWF4NUxkMXBSdlRnTXlWRkR4 -X PUT https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/backoffice/pricing-rpm/paul-5.4.war

node('npm'){

currentBuild.result = "SUCCESS"
jdk=tool name:'openjdk-11.0.7.10-0'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"
node="/app/jenkins/.nvm/versions/node/v14.17.1/bin/node"

echo "Build_Type = ${userInput}"
sh "whoami"

	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			//listGithubBranches(gitCreds,gitRepository)
			//echo "Checking out p${env.BRANCH_SCOPE}p"
			//gitBranch = "${env.BRANCH_SCOPE}"
			//cleanWs()
			//githubCheckout(gitCreds, gitRepository, "${env.BRANCH_SCOPE}")
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		stage('Build'){

			if (userInput == 'Build') {
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

			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."

				withCredentials([usernamePassword(credentialsId: 'scm-incomm', passwordVariable: 'gitPass', usernameVariable: 'gitUser')]) {

					sh "${maven} -X -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
					sleep(3)
					sh "${maven} -X -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
				}
				sh "cp target/${artifactId}*.${artExtension} ${artifactName}"

			} else {
				echo "no build"
			}

		} //stage

		stage('SonarQube Quality Gate Check') {
			if (userInput == 'Build'){
					
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

			if (userInput == 'Promote') {
				
			artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
			sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"
			
			//	artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage('Deployment'){
			
			if (userInput == 'Promote'){

				echo "Deploying to ${target_env}"
				deployComponents(target_env, targets[target_env], "${artifactName}")

			} else if ((userInput == 'Build') && (runCypress == 'true')) {
				
				echo "Deploying to automation server for Cypress test suite ${suiteName}"
				deploy_Automation(automation_env, targets[automation_env], "${artifactName}")
				runCypress(suiteName, target_env)
				target_env = "dev"
				echo ""
				echo "Deploying to ${target_env}"
				echo ""
				deployComponents(target_env, targets[target_env], "${artifactName}")
			} else {
				target_env = "dev"
				echo "Deploying to ${target_env}"
				deployComponents(target_env, targets[target_env], "${artifactName}")
			}

		}



	} catch (exc) {

			currentBuild.result = 'FAILURE'
			//sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)
			echo 'ERROR:  ' + exc.toString()
			throw exc

	} finally {

		stage('Notification'){
			
		sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)	
		}
	}

} //end of node

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def runCypress(suiteName, target_env){
	
	echo "DEBUG runCypress ${suiteName}"
	sshagent([credsName]) {
		sh"""
			#npx cypress run
			npm install
			#npx cypress run server=${target_env} --env SUITE=${suiteName} --record --key e0962e78-2261-4902-86f1-ed83ca078229
			#npx cypress run server=qa --spec "cypress/integration/test_flows/regular-cases/happy-path.spec.js" --record --key e0962e78-2261-4902-86f1-ed83ca078229
			npx cypress run server=${target_env} --browser chrome --spec "cypress/integration/test_flows/regular-cases/happy-path.spec.js" --record --key e0962e78-2261-4902-86f1-ed83ca078229
		
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
