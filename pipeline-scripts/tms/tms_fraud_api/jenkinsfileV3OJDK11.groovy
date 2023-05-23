import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk


gitRepository = "https://github.com/InComm-Software-Development/cfes-tms-fraudmodule"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

credsName = "scm_deployment"
// inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target_env}"

testName = "myTest"

targets = [
	'dev': ['sdtmsfrdapp01v','sdtmsfrdapp02v','sdtmsfrdapp03v','sdtmsfrdapp04v'],
	'qa': ['sqfrdapp01v','sqfrdapp02v','sqltmsfrdapp01v','sqltmsfrdapp02v']
]

emailDistribution="ppiermarini@incomm.com"
//General pipeline 

artifactDeploymentLoc="/opt/pivotal/pivotal-tc-server-4.0.11.RELEASE/instances/fraud-ws-instance/webapps"
libDeploymentLoc="/opt/pivotal/pivotal-tc-server-4.0.11.RELEASE/instances/fraud-ws-instance/lib"
instanceLogs="/opt/pivotal/pivotal-tc-server-4.0.11.RELEASE/instances/fraud-ws-instance/logs/"

def artifactloc = "${env.WORKSPACE}"

serviceName = "fraud-ws-instance"
star="*"
pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

//Artifact Resolver	input specifics
repoId ='maven-release'
groupId ='com.incomm.services.tms.fraud'
artifactId ='tms-fraud-service'
env_propertyName ='ART_VERSION'
artExtension ='war'
artifactName ='fraud-api-ws-2.war'

artifactFolder = 'fraud-api-ws-2'

//globals
test_suite = "none"
user = "tcserver"
group = "pivotal"

//chmod permission
filePermission = "644"
folderPermission = "775"

userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"



//userInput = InputAction()

node('linux'){
	
jdk = tool name: 'openjdk-11.0.5.10'
	env.JAVA_HOME = "${jdk}"
	echo "jdk installation path is: ${jdk}"
	sh "${jdk}/bin/java -version"

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
					sh "${maven} clean deploy -X -e -U -DskipTests sonar:sonar"
					sh "cp tms-fraud-service/target/${artifactName} . "
				}

			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"

			} else if (userInput == 'Test') {

				echo "Running only tests"
				
			} else {
				echo "no build"
			}

		} //stage


stage('SonarQube Quality Gate Check') {
		//if (userInput == 'Build'){
				
			//sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
			//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
			
			//timeout(time: 3, unit: 'MINUTES') {	
				//def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
					//if (qg.status != 'OK') {
					//error "Pipeline aborted due to quality gate failure: ${qg.status}"
					//}
			//echo "after QG timeout"
			//}
				
		//} else {
				echo "Quality Gate not needed for Release or Promote"
		//}
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
		
		if (target_env.contains('prod')){
			echo "SPLIT target_env"
			envPath = target_env.split('_')
			envPath = envPath[0]
			echo "the prod path ${envPath}"
		} else {
			
			envPath = "${target_env}"
		}

		if ((userInput == 'Build') || (userInput == 'Promote')) {
			if (userInput == 'Build') {

				echo "Build always deployes to "
			}
			//prelimEnv(target_env, relVersion)
			deployComponents(target_env, targets[target_env], "${artifactName}")
		
			//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")

		} else {
			echo "not deploying during a release build"
		}

	}

//	stage('Testing'){
//		node('QAAutomation'){
//			cleanWs()
//			if ((userInput == 'Build') || (userInput == 'Promote') ||  (userInput == 'Test')) {
//				gitRepository = "https://github.com/InComm-Software-Development/ccl-qa-automation-spil"
//				gitBranch = ""
//				gitCreds = "scm-incomm"
//				dir('ccl-qa-automation-spil'){
//					githubCheckout(gitCreds, gitRepository, gitBranch)
//				}
//				sh "java -jar /opt/SmartBear/ready-api-license-manager/ready-api-license-manager-1.2.7.jar -s 10.42.97.167:1099 < /opt/SmartBear/ready-api-license-manager/licensecode.txt"
//				echo "running test suite: ${test_suite}"
//
//				if(test_suite == "none"){
//					echo "no testsuite was selected, tests were not run."
//				} else {
//					if(test_suite == "all"){
//						SoapUIPro(environment: target_env, pathToProjectFile: '/app/jenkins/workspace/CCLP-SPIL/ccl-qa-automation-spil', pathToTestrunner: '/opt/SmartBear/ReadyAPI-2.8.2/bin/testrunner.sh', projectPassword: '', testCase: '', testSuite: '')
//					}
//					else{
//						SoapUIPro(environment: target_env, pathToProjectFile: '/app/jenkins/workspace/CCLP-SPIL/ccl-qa-automation-spil', pathToTestrunner: '/opt/SmartBear/ReadyAPI-2.8.2/bin/testrunner.sh', projectPassword: '', testCase: '' , testSuite: test_suite)
//					}
//					publishHTML(target: [
//						allowMissing: false,
//						alwaysLinkToLastBuild: false,
//						keepAll: true,
//						reportDir: 'ReadyAPI_report/',
//						reportFiles: 'index.html',
//						reportName: "Test Report"
//					])
//					smokeTesting(target_env, targets[target_env], testName)
//				}
//			} else {
//				echo "not testing during a release build"
//			}
//		}
//
//	}


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
	
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactFolder}'
		scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/${envPath}/${star} root@${target_hostname}:${libDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${libDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${filePermission} ${libDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${folderPermission} ${libDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
	"""
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
