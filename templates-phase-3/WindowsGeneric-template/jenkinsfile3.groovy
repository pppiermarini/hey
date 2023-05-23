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

gitRepository = "https://github.com/InComm-Software-Development/hello-world.git"
gitBranch = "${Branch}"  //define in job
gitCreds = "scm-incomm"

credsName = "scm_deployment"

// inputs from build with parameters
userInput = "${BUILD_TYPE}" //define in job
target_env = "${target}" //define in job
test_suite = "none"
testName = "myTest"


		
targets = [
	'dev': ['10.42.17','10.42.17'],
	'qa': ['10.42.81.','10.42.81.']
]


emailDistribution="ppiermarini@incomm.com"
//General pipeline .

artifactDeploymentLoc = "E:\\my\\app\\path"
configDeploymentLoc = "E:\\my\\config\\path"
LibDeploymentLoc = "E:\\my\\lib\\path"

def artifactloc = "${env.WORKSPACE}"

serviceName = "your servive name"

pipeline_id = "${env.BUILD_TAG}"

maven = "E:\\opt\\apache-maven-3.2.1\\bin\\mvn"

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'hello-world-testing'
artifactId = 'hello-world'
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

user="jboss"
group="jboss"
chmod = "755"

//userInput = InputAction()

node('windows'){
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
				//withSonarQubeEnv('sonarqube.incomm.com'){
				withSonarQubeEnv('sonar'){
					bat "${maven} clean compile -f pom.xml -U -B -DskipTests sonar:sonar"
					
				//	bat "cp target/${artifactId}*.${artExtension} ${artifactName}"
				}

			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				bat "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				bat "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease

			} else if (userInput == 'Test') {

				echo "Running only tests"
				
			} else {
				echo "Doing a Promote"
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
					} else {
						echo "Quality gate passed"
						sh "${maven} package deploy"
					}
			echo "after QG timeout"
			}
				
		} else {
				//echo "Quality Gate not needed for Release or Promote"
			echo "No Quality gate "
		}
}//end QualityGate


		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote') {
				
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if ((userInput == 'Build') || (userInput == 'Promote')) {

//NOTICE: deployment commented out. uncomment when ready

			//	deployComponents(target_env, targets[target_env], "${artifactName}")
				//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")

			} else {
				echo "not deploying during a release build"
			}

		}


currentBuild.result = 'SUCCESS'

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


//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
// ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop &>/dev/null'


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"

	bat """
		powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Stop-Service
		dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
		del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\$artifactId*.${artExtension}
		sleep(4)
		echo "deploying ${artifactName}"
		copy /Y ${artifactName} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
		echo "copying all configs"
		copy /Y config\\${envName}\\*.* \\\\${target_hostname}\\${configDeploymentLoc}\\
		sleep(4)
		echo "Copying the version"
		dir \\\\${target_hostname}\\${configDeploymentLoc}\\
		copy /Y version.txt \\\\${target_hostname}\\${configDeploymentLoc}\\
		dir \\\\${target_hostname}\\${configDeploymentLoc}\\
		sleep(4)
		powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service	
	"""
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
