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

gitRepository = "https://github.com/InComm-Software-Development/sop-lit-productvalidator-ui"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

// inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target_env}"
test_suite = "${test_suite}"
testName = "myTest"

targets = [
	'dev': ['10.42.84.130']
]

emailDistribution="ppiermarini@incomm.com dstovall@incomm.com"
//General pipeline 

artifactDeploymentLoc = "/var/opt/lit-document-service"
def artifactloc = "${env.WORKSPACE}"

serviceName = "LITDocumentService"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.lit.document'
artifactId = 'lit-document-service'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''


//globals
userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"
test_suite = "none"



//userInput = InputAction()

node('linux'){
currentBuild.result = 'SUCCESS'

jdk=tool name:'openjdk-11.0.7.10-0'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"
echo "Build_Type = ${userInput}"
sh "whoami"
	
	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		stage('Build'){

			if (userInput == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com'){
					sh "${maven} clean deploy -f pom.xml -U -B sonar:sonar"
					sh "cp target/${artifactId}*.${artExtension} ."
					//sh "${maven} compile -f pom.xml  sonar:sonar"
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
				}

			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."

				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"

			} else if (userInput == 'Test') {

				echo "Running only tests"
				
			} else {
				echo "no build"
			}

		} //stage


stage('SonarQube Quality Gate Check') {
		if (userInput == 'Build'){
				
			sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
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
				getFromArtifactory()

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if ((userInput == 'Build') || (userInput == 'Promote')) {
				if (userInput == 'Build') {
					target_env = 'dev-a'
					echo "Build always deployes to "
				}
				prelimEnv(target_env, relVersion)
				deployComponents(target_env, targets[target_env], "${artifactId}-${relVersion}.${artExtension}")
				//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")

			} else {
				echo "not deploying during a release build"
			}

		}

		stage('Testing'){
			node('QAAutomation'){
				cleanWs()
				if ((userInput == 'Build') || (userInput == 'Promote') ||  (userInput == 'Test')) {
					gitRepository = "https://github.com/InComm-Software-Development/ccl-qa-automation-spil"
					gitBranch = ""
					gitCreds = "scm-incomm"
					dir('ccl-qa-automation-spil'){
						githubCheckout(gitCreds, gitRepository, gitBranch)
					}
					sh "java -jar /opt/SmartBear/ready-api-license-manager/ready-api-license-manager-1.2.7.jar -s 10.42.97.167:1099 < /opt/SmartBear/ready-api-license-manager/licensecode.txt"
					echo "running test suite: ${test_suite}"

					if(test_suite == "none"){
						echo "no testsuite was selected, tests were not run."
					} else {
						if(test_suite == "all"){
							SoapUIPro(environment: target_env, pathToProjectFile: '/app/jenkins/workspace/CCLP-SPIL/ccl-qa-automation-spil', pathToTestrunner: '/opt/SmartBear/ReadyAPI-2.8.2/bin/testrunner.sh', projectPassword: '', testCase: '', testSuite: '')
						}
						else{
							SoapUIPro(environment: target_env, pathToProjectFile: '/app/jenkins/workspace/CCLP-SPIL/ccl-qa-automation-spil', pathToTestrunner: '/opt/SmartBear/ReadyAPI-2.8.2/bin/testrunner.sh', projectPassword: '', testCase: '' , testSuite: test_suite)
						}
						publishHTML(target: [
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: true,
							reportDir: 'ReadyAPI_report/',
							reportFiles: 'index.html',
							reportName: "Test Report"
						])
						smokeTesting(target_env, targets[target_env], testName)
					}
				} else {
					echo "not testing during a release build"
				}
			}

		}


	} catch (exc) {
		echo "Muy Mal"
		stage('Notification'){
			currentBuild.result = 'FAILURE'
			sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)
			echo 'ERROR:  ' + exc.toString()
			throw exc
		}

	} finally {
		echo " Muy Bien "
		stage('Notification'){
			currentBuild.result = 'SUCCESS'
			//xsendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)	
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def prelimEnv(target_env, relVersion){

	// prelim staging if needed

	echo "${target_env}"
	echo "Selected=  ${artifactId}-${relVersion}.${artExtension}"
	echo "DEPLOYING TO ${target_env}"
	echo "relVersion= ${relVersion}"
	writeFile file: "relVersion.txt", text: relVersion

	echo "DEPLOYING TO ${target_env}"

	localArtifact = "${artifactId}-${relVersion}.${artExtension}"
	remoteArtifact = "${artifactId}-${relVersion}.${artExtension}"

	echo " local artifact=  $localArtifact"
	echo "remote artifact=  $remoteArtifact"
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
	sh """
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv $artifactDeploymentLoc/${artifactId}*.${artExtension}.*'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv $artifactDeploymentLoc/${artifactId}*'
			scp -q -i ~/.ssh/pipeline ${artifactId}*.${artExtension} root@${target_hostname}:$artifactDeploymentLoc/
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/chown -R jboss:jboss $artifactDeploymentLoc/${artifactId}*.${artExtension}'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find /srv/jboss-eap-7.0/standalone/deployments/ -type f -name ${artifactId}*.failed | wc -l' > commandresult
	"""
	def r = readFile('commandresult').trim()
	echo "arr= p${r}p"
	if (r == "1") {
		echo "failed deployment"
		currentBuild.result = 'FAILED'
	} else {
		echo "checking for deployed"
	}
	try {
		timeout(1) {
			waitUntil {
				sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find /srv/jboss-eap-7.0/standalone/deployments/ -type f -name ${artifactId}*.deployed | wc -l' > commandresult"""
				def a = readFile('commandresult').trim()
				echo "arr= p${a}p"
				if (a == "1") {
					return true;
				} else {
					return false;
				}
			}

		}
	} catch (exception) {
		echo "${artifactId}-${relVersion}.${artExtension} did NOT deploy properly. Please investigate"
		abortMessage = "${artifactId}-${relVersion}.${artExtension} did NOT deploy properly. Please investigate"
		currentBuild.result = 'FAILED'
	}
}


//def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

//	def stepsInParallel =  targets.collectEntries {
//		[ "$it" : { md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
//	}
//	parallel stepsInParallel

//}


//def md5(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

//	def validate2 = md5(targets, artifactDeploymentLoc, remoteArtifact, localArtifact)
//	echo "validate2=  $validate2"
//		if("${validate2}" != "0"){
//		echo "${localArtifact} files are different 1"
//		currentBuild.result = 'ABORTED'
//		error('Files do not match...')
//		}else{
//		echo "${localArtifact} files are the same 0"
//		}
//}


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
