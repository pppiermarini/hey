import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// launchit-document-services
//#################

@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk

gitRepository = "https://github.com/InComm-Software-Development/sop-lit-document.git"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

credsName = "scm_deployment"

// inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target_env}"
test_suite = "None"
testName = "myTest"

targets = [
	'dev': ['10.42.84.130'],
	'qa': ['10.42.49.209'],
	'uat' :['10.44.0.155'],
	'automation' : ['10.42.84.135'],
	'stg': ['AQSPL02APL2V'],
	'prod1': ['10.40.2.77'],
	'prod2': ['10.40.2.78'],
	'prod3': ['10.40.2.79']
]

emailDistribution="ppiermarini@incomm.com rkantareddy@incomm.com"
//General pipeline 

artifactDeploymentLoc = "/var/opt/lit-document-service"
instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/launchworkflow/logs/"
def artifactloc = "${env.WORKSPACE}"

serviceName = "LITDocumentService"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.8.6/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'incomm-release'
groupId = 'com.incomm.lit.document'
artifactId = 'lit-document-service'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'lit-document-service.jar'

//globals
runQualityGate = "true"
userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"
test_suite = "none"
chmod="744"
user = "tcserver"
group = "pivotal"

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
					
					//sh "${maven} compile -f pom.xml  sonar:sonar"
					//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=app/"
					sh "cp target/${artifactId}*.${artExtension} ."
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

        stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build') {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == 'true') {
                   qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }

/*stage('SonarQube Quality Gate Check') {
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
*/
		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote') {
				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

				list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
				echo "the list contents ${list}"
				artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
				parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
				sleep(3)
				artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if ((userInput == 'Build') || (userInput == 'Promote')) {
				if (userInput == 'Build') {
					target_env = 'dev'
					echo "Build always deploys to dev env"
				}

				deployComponents(target_env, targets[target_env], "${artifactName}")

			} else {
				echo "not deploying during a release build"
			}

		}

		stage('Testing'){
			echo "no tests at this time"
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
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${artifactDeploymentLoc}/${artifactId}*.${artExtension}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv ${artifactDeploymentLoc}/${artifactId}*.${artExtension}'
			scp -q -o StrictHostKeyChecking=no ${artifactId}*.${artExtension} root@${target_hostname}:$artifactDeploymentLoc/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLoc}/${artifactId}*.${artExtension}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactId}*.${artExtension}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
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
