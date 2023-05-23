import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


gitRepository = "https://github.com/InComm-Software-Development/bif-dds"
gitBranch = "${Branch}" 
gitCreds = "scm-incomm"

credsName = "scm_deployment"

// inputs from build with parameters
userInput = "${BUILD_TYPE}" //define in job
target_env = "${target}" //define in job
test_suite = "none"
testName = "myTest"


targets = [
	'dev-01': ['sdbifrlt323V'],
	'dev1-01': ['sdaifrtm01v'],
	'dev1-02': ['sdaifrtm02v'],
	'qa-01': ['sqbifrtm289V'],
	'qa-02': ['Sqbifrtm290V'],
	'qa1-01': ['sqaifrtm01V'],
	'qa1-02': ['Sqaifrtm02V'],
	'uat-01': ['subifrtm306V'],
	'uat-02' : ['subifrtm332V'],
	'uat1-01' : ['staifrtm01V'],
	'uat1-02' : ['staifrtm02V'],
	'prod' :['??']
]

emailDistribution="ppiermarini@incomm.com"
//General pipeline .

artifactDeploymentLoc = "C\$\\bif-dds-ms\\bif-dds-ms"
//configDeploymentLoc = "E:\\my\\config\\path"
//LibDeploymentLoc = "E:\\my\\lib\\path"

def artifactloc = "${env.WORKSPACE}"

serviceName = "BIF-DDS"

pipeline_id = "${env.BUILD_TAG}"

maven = "E:\\opt\\apache-maven-3.2.1\\bin\\mvn"

///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.b2b'
artifactId = 'bif-dds'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'bif-dds.jar'

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

		
node('windows'){
	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed


		stage('checkout'){
			//cleanWs()
			//listGithubBranches(gitCreds,gitUrl)
			//echo "Checking out p${env.BRANCH_SCOPE}p"
			//sleep(10)
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}

		stage('Build'){

			if (userInput == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com'){
				//withSonarQubeEnv('sonar'){
					bat "${maven} clean deploy -f pom.xml -U -B -DskipTests sonar:sonar"
//					bat "${maven} clean deploy -f pom.xml -U -B -DskipTests sonar:sonar"
					
					//bat "del target/${artifactName}.original"
					bat "copy target/${artifactName} ."
					
				}

			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def developmentVersion = pom.getVersion()
				def str = developmentVersion.split('-')
				def releaseVersion = str[0]
				echo " maven release= " + "${releaseVersion}"
				bat "${maven} -B org.apache.maven.plugins:maven-release-plugin:clean org.apache.maven.plugins:maven-release-plugin:prepare org.apache.maven.plugins:maven-release-plugin:perform -Darguments=\'-Dmaven.javadoc.skip=true\' -DskipTests -Darguments=-DskipTests -DignoreSnapshots=true -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion}"
				//sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				//sleep(3)
				//sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(4)

				writeFile file: "mavenrelease", text: releaseVersion

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

				deployComponents(target_env, targets[target_env], "${artifactName}")
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

	bat """
		 powershell Get-Service -ComputerName ${target_hostname} -Name ${serviceName} ^| Stop-Service
		 del \\\\${target_hostname}\\c\$\\bif-dds-ms\\bif-dds-ms\\${artifactName}
		 copy /Y ${artifactName} \\\\${target_hostname}\\${artifactDeploymentLoc}\\"
		 powershell Get-Service -ComputerName ${target_hostname} -Name ${serviceName} ^| Start-Service
	"""
}

//Another PS command way to copy a artifact or start the service
//bat "copy /Y \"${env.WORKSPACE}\\bif-dds-ms\\target\\bif-dds*.jar\" \\\\${HOSTNAME[i]}\\${DeployLocation}"
//bat "powershell Get-Service ${env.serviceName} -ComputerName ${HOSTNAME[i]} ^| Start-Service"

//To Check Md5sum
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
