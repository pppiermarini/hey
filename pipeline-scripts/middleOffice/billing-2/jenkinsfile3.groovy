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

gitRepository = "https://github.com/InComm-Software-Development/ipbr-pipeline.git"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

gitConfigsRepo = "https://github.com/InComm-Software-Development/ipbr-billing2-configs-lle.git"
gitConfigsBranch = "${configBranch}"

credsName = "scm_deployment"

// inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target}"
test_suite = "none"
testName = "myTest"

		
targets = [
	'dev': ['10.42.20.10','10.42.20.11']
]


emailDistribution="ppiermarini@incomm.com"
//General pipeline . 

artifactDeploymentLoc = "/var/opt/billing"
configDeploymentLoc = "/var/opt/billing/configs"
libDeploymentLoc = "/var/opt/billing/lib"


configFiles = "configs"

astrik = "*"
dot = "."

def artifactloc = "${env.WORKSPACE}"

serviceName = "billing2"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.ipbr'
artifactId = 'billing-api'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'billing2.jar'

//globals
userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"
systemctl = "/bin/systemctl"
user="billing2"
group="billing2"
chmod = "755"
dev_env="dev"
//userInput = InputAction()

node('linux'){
	
jdk=tool name:'openjdk-11.0.5.10'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"


	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
			dir('configs'){
				githubCheckout(gitCreds, gitConfigsRepo, gitConfigsBranch)
			}
		}

		stage('Build'){
echo"user input  ${userInput}"
			if (userInput == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com
				//withSonarQubeEnv('sonarqube.incomm.com'){
				withSonarQubeEnv('sonar'){
					sh "${maven} clean deploy -f pom.xml -U -B -DskipTests"
					
					sh "cp billing-api/target/${artifactId}*.${artExtension} ${artifactName}"
					
					//sh "${maven} compile -f pom.xml  sonar:sonar"
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
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
				echo "Doing a Promote"
			}

		} //stage


stage('SonarQube Quality Gate Check') {
		if (userInput == 'Build'){
				
	///		sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
			//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
			
	//		timeout(time: 3, unit: 'MINUTES') {	
	//			def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
	//				if (qg.status != 'OK') {
	//				error "Pipeline aborted due to quality gate failure: ${qg.status}"
	//				}
	//		echo "after QG timeout"
	//		}
	//			
		} else {
				//echo "Quality Gate not needed for Release or Promote"
			echo "No Quality gate "
		}
}//end QualityGate


		//select the artifact 
		stage('Get Artifact'){

			if ((userInput == 'Promote') || (userInput == 'Build&Promote')) {
				
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote') {

				//prelimEnv(target_env, relVersion)
				deployComponents(target_env, targets[target_env], "${artifactName}")
				//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")

			} else if (userInput == 'Build&Promote') {
				
				//deployComponents(dev_env, targets[dev_env], "${artifactName}")
				echo "deploying to dev"
				
			} else {
				
				echo "not deploying during a release build"
				
			}

		}

//		stage('Testing'){
//			node('QAAutomation'){
//				cleanWs()
//				if ((userInput == 'Build') || (userInput == 'Promote') ||  (userInput == 'Test')) {
//					gitRepository = ""
//					gitBranch = ""
//					gitCreds = "scm-incomm"
//					dir('test'){
//			//			githubCheckout(gitCreds, gitRepository, gitBranch)
//					}
//			//		sh "java -jar /opt/SmartBear/ready-api-license-manager/ready-api-license-manager-1.2.7.jar -s 10.42.97.167:1099 < /opt/SmartBear/ready-api-license-manager/licensecode.txt"
//			//		echo "running test suite: ${test_suite}"////

//				f(test_suite == "none"){
//						echo "no testsuite was selected, tests were not run."
//					} else {
//						if(test_suite == "all"){
//							SoapUIPro(environment: target_env, pathToProjectFile: '/app/jenkins/workspace/CCLP-SPIL/ccl-qa-automation-spil', pathToTestrunner: '/opt/SmartBear/ReadyAPI-2.8.2/bin/testrunner.sh', projectPassword: '', testCase: '', testSuite: '')
//						}
//						else{
//							SoapUIPro(environment: target_env, pathToProjectFile: '/app/jenkins/workspace/CCLP-SPIL/ccl-qa-automation-spil', pathToTestrunner: '/opt/SmartBear/ReadyAPI-2.8.2/bin/testrunner.sh', projectPassword: '', testCase: '' , testSuite: test_suite)
//						}
//						publishHTML(target: [
//							allowMissing: false,
//							alwaysLinkToLastBuild: false,
//							keepAll: true,
//							reportDir: 'ReadyAPI_report/',
//							reportFiles: 'index.html',
//							reportName: "Test Report"
//						])
//						smokeTesting(target_env, targets[target_env], testName)
//					}
//				} else {
//					echo "not testing during a release build"
//				}
//			}
		echo "No Testing "
//		} // end testing stage


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
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${systemctl} stop ${serviceName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${artifactName}*'
			scp -q -o -p StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:$artifactDeploymentLoc/
			scp -q -o StrictHostKeyChecking=no ${configFiles}/${astrik}.xml root@${target_hostname}:$configDeploymentLoc/
			scp -q -o StrictHostKeyChecking=no ${configFiles}/${astrik}.properties root@${target_hostname}:$configDeploymentLoc/
			scp -q -o -p StrictHostKeyChecking=no ${configFiles}/${dot}project root@${target_hostname}:$configDeploymentLoc/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${$configDeploymentLoc}/README.md'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLoc}/${artifactName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${systemctl} start ${serviceName}'
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
