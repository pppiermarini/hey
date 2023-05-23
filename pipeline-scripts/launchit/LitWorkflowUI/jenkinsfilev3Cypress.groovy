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

// inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target_env}"

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
//splwfapp02v 10.40.2.78   sysid  6940a49cdb106384a22e2b35ca961951  
//splwfapp03v 10.40.2.79    sysid  8880ac9cdb106384a22e2b35ca96198a
//splwfapp01v 10.40.2.77     sysid   2d40a49cdb106384a22e2b35ca96197c
//LaunchIT(Product Launch) sysid   951acc53db97819049f75a6a6896196f

emailDistribution="ppiermarini@incomm.com jkesineni@incomm.com"
//General pipeline 

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

//globals
userApprove = "Welcome to the new Jenkinsfile"
relVersion = "null"

user = "tcserver"
group = "pivotal"



node('npm'){
nodejs="/app/jenkins/.nvm/versions/node/v14.15.3/bin"
//nodejs=tool name:'NodeJS_v16.19.1'
env.PATH="${nodejs}:${env.PATH}"

currentBuild.result = "SUCCESS"
jdk=tool name:'openjdk-11.0.7.10-0'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"
//node="/app/jenkins/.nvm/versions/node/v14.17.1/bin/node"

echo "Build_Type = ${userInput}"
sh "whoami"

	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			//listGithubBranches(gitCreds,gitRepository)
			echo "target_env: ${target_env}"
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
				//sh "${node} --version"
				//sh "nvm use 14.17.1"
				sh "node --version"
				//sh "source ~/.nvm/nvm.sh"
				sleep(5)
				withSonarQubeEnv('sonarqube.incomm.com'){
					sh "${maven} clean deploy -X sonar:sonar" //-Dsonar.branch.name=${gitBranch}
				}

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

		stage('Cypress'){
			if ((userInput == 'Build') && (runCypress == 'true')){
				
				echo "Deploying to automation server for Cypress test suite ${suiteName}"
				deploy_Automation(automation_env, targets[automation_env], "${artifactName}")
				runCypress(suiteName, target_env)
				target_env = "dev"
				echo ""
				echo "Deploying to ${target_env}"
				echo ""
				deployComponents(target_env, targets[target_env], "${artifactName}")
				
			} else if ((userInput == 'NoBuild') && (runCypress == 'true')){
				//This NoBuild option is for QA
				//echo "Deploying to automation server for Cypress test suite ${suiteName}"
				//deploy_Automation(automation_env, targets[automation_env], "${artifactName}")
				echo "Enter NoBuild"
				runCypress(suiteName, target_env)		
				
			}else {
				echo "Cypress Not RUN"
			}
			
			
		}

		stage('Deployment'){
			
			if (userInput == 'Promote'){

				echo "Deploying to ${target_env}"
				deployComponents(target_env, targets[target_env], "${artifactName}")

			}else{
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

def smokeTesting(envName, targets, testName){

	echo "my env= ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { tests(it, envName, testName) }]
	}
	parallel stepsInParallel

}//end smoketesting

def tests(target, envName, testName){

	echo " Smoke Testing on ${target}"
	echo "my test = ${testName}"
	sleep(1)
	dir('testresults'){
		//println "Run Test Script"
		//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
		// String results = readFile 'testresults.xml'
	}
	//if(1 ){
	//	println "ERROR todo "
	//} else {
	//	println "results"
	//}
}


///// The End
