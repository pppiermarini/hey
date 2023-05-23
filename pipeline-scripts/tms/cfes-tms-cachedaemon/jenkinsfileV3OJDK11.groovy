import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
//cfes-finspect-cachedaemon
//#################

@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk

gitRepository = "https://github.com/InComm-Software-Development/cfes-tms-cachedaemon.git"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

credsName = "scm_deployment"
// inputs from build with parameters
userInput = "${BUILD_TYPE}"
targetEnv = "${target_env}"

testName = "myTest"

targets = [
	'dev1_sdtmsfrdapp01v': ['sdtmsfrdapp01v'],
	'dev2_sdtmsfrdapp02v': ['sdtmsfrdapp02v'],
	'dev3_sdtmsfrdapp03v': ['sdtmsfrdapp03v'],
	'dev4_sdtmsfrdapp04v': ['sdtmsfrdapp04v'],
	'qa1_sqltmsfrdapp01v': ['sqltmsfrdapp01v'],
	'qa2_sqltmsfrdapp02v': ['sqltmsfrdapp02v']
]

emailDistribution="dstovall@incomm.com ppiermarini@incomm.com"
//General pipeline 

artifactDeploymentLoc = "/opt/cfes-tms-cachedaemon"
libDeploymentLoc="/var/opt/pivotal/pivotal-tc-server-standard/tmsfscachedaemon/lib"
//instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/tmsfscachedaemon/logs"
def artifactloc = "${env.WORKSPACE}"

serviceName = "cfes-tms-cachedaemon"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

//Artifact Resolver	input specifics
repoId ='maven-release'
groupId ='com.incomm.cache.fraud'
artifactId ='cfes-tms-cachedaemon'
env_propertyName ='ART_VERSION'
artExtension ='jar'
artifactName ='.jar'

artifactRealName = 'cfes-tms-cachedaemon' 

//globals
test_suite = "none"
user = "tcserver"
group = "pivotal"
filePermission = "644"
folderPermission = "755"
star = "*"
prop = "*.properties"
yml = "*.yml"
xml = "*.xml"

userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"
myenv = ""


//userInput = InputAction()

node('linux'){
	
jdk=tool name:"openjdk-11.0.7.10-0"
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"	
	
String[] ENV;
ENV = targetEnv.split('_');
echo "p${ENV[0]}p"
myenv = "${ENV[0]}"
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
					sh "${maven} clean deploy -e -U -DskipTests sonar:sonar"
				//sh "cp target/${artifactName} ."

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
		if (userInput == 'Build'){
				
	//		sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
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
				echo "Quality Gate not needed for Release or Promote"
		}
}//end QualityGate

		//select the artifact 
		stage('Approval'){
            			
			if (userInput == 'Promote'){

               if ((ENV[0] =='prod'))
                {
                    echo "Inside approval block"
                    operators= "['awadhwa','amohammad','howilliams','dstovall']"
                    def Deployment_approval = input message: 'Deploying to PROD', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
                    echo "${Deployment_approval}"
                    approval_status = "${Deployment_approval['approval_status']}"
                    def operator = "${Deployment_approval['approver']}"
					String op = operator.toString()

                    if (approval_status == 'Proceed'){
                        echo "Operator is ${operator}"
                        if (operators.contains(op))
      		            {
                            echo "${operator} is allowed to deploy into ${targetEnv}"
		                }
		                else
		                {
		                    throw new Exception("Throw to stop pipeline as user not in approval list")
		                }
                    }else {
                    throw new Exception("Throw to stop pipeline as user selected abort")
                    }
                }
			} 
        }


		stage('Get Artifact'){
			if (userInput == 'Promote'){
				//getFromArtifactory()
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	

			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if ((userInput == 'Build') || (userInput == 'Promote')) {
				if (userInput == 'Build') {

					echo "Build always deployes to "
				}
				//prelimEnv(target_env, relVersion)
				deployComponents(target_env, targets[target_env], "${artifactId}-${relVersion}.${artExtension}")
			
				//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")

			} else {
				echo "not deploying during a release build"
			}

		}
/*
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
*/

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
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}'
		scp -q -o StrictHostKeyChecking=no ${artifactRealName}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${myenv}/${star} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${prop} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${yml} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${yml} root@${target_hostname}:${libDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/'
	"""
	}
}

//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
//ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
//ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}'

//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
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
