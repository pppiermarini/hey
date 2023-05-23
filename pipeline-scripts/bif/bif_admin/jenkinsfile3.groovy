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

gitRepository = "https://github.com/InComm-Software-Development/BIFAdmin.git"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

credsName = "scm_deployment"

//inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target_env}"
test_suite = "None"
testName = "myTest"

targets = [
	'dev-bifadmin': ['sdbifmgr329v'],
	'dev-bifadmin1': ['sdbifmgr329v'],
	'qa-bifadmin': ['sqbifmgr292v'],
	'qa-bifadmin1': ['sqbifmgr292v'],
	'uat-bifadmin': ['suifemgr336v'],
	'uat-bifadmin1': ['suifemgr336v'],
	'prod': ['']
]

emailDistribution="ppiermarini@incomm.com"
//General pipeline 

artifactDeploymentLoc = "/var/opt/pivotal/pivotal-tc-server/bifadmin/webapps"
artifactDeploymentLoc1 = "/var/opt/pivotal/pivotal-tc-server/bifadmin1/webapps"
instanceLogs = "/var/opt/pivotal/pivotal-tc-server/bifadmin/logs"
instanceLogs1 = "/var/opt/pivotal/pivotal-tc-server/bifadmin1/logs"
def artifactloc = "${env.WORKSPACE}"

serviceName = "bifadmin"
serviceName1 = "bifadmin1"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.bif'
artifactId = 'BIFAdmin'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'BIFAdmin.war'


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

node('linux1'){
	

jdk=tool name:'jdk1.8.0_202'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"	
	
	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			if ((userInput == 'Build') || (userInput == 'Release')) {
				githubCheckout(gitCreds, gitRepository, gitBranch)
			} else {
				echo "skipping checkout"
			}
		}

		stage('Build'){

			if (userInput == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com'){
					echo "building sonar with java 8"
					sh"${maven} clean deploy -X -e -U -DskipTests sonar:sonar"
					echo "switching to java 7 for build"
					jdk=tool name:'jdk1.7.0_151'
					env.JAVA_HOME="${jdk}"
					echo "jdk installation path is: ${jdk}"
					sh "${jdk}/bin/java -version"
					sh"${maven} clean deploy -X -e -U -DskipTests"

//
//					sh "${maven} sonar:sonar"
//					sh "cp target/${artifactId}*.${artExtension} ${artifactName}"
//
//				} //withSonarQubeEnv
				}
			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def developmentVersion = pom.getVersion();
				def str = developmentVersion.split('-');
				def releaseVersion = str[0];
				echo " maven release= " + "${releaseVersion}"
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:clean org.apache.maven.plugins:maven-release-plugin:prepare org.apache.maven.plugins:maven-release-plugin:perform -Darguments=\'-Dmaven.javadoc.skip=true\' -DskipTests -Darguments=-DskipTests -DignoreSnapshots=true -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion}"
				//sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				//sleep(3)
				//sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"


			} else if (userInput == 'Test') {

				echo "Running only tests"
				
			} else {
				echo "no build"
			}

		} //stage


stage('SonarQube Quality Gate Check') {
	//	if (userInput == 'Build'){
				
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
	//	} else {
	//			echo "Quality Gate not needed for Release or Promote"
	//	}
}//end QualityGate


		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote') {
			echo "Get Artifact"
			//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
            
			withCredentials([usernamePassword(credentialsId: 'maven_read_only', passwordVariable: 'ArtPass', usernameVariable: 'ArtUser')]) {
                sh """
                    groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1
                    wget --user "${ArtUser}" --password "${ArtPass}" -O ${artifactName} --no-check-cert --no-verbose http://maven.incomm.com/artifactory/${repoId}/\$groupIdDir/$artifactId/${artifactVersion}/${artifactId}-${artifactVersion}.${artExtension}  || exit 1
                """
            }

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage("Deployment to ${target_env}"){

			if (userInput == 'Promote') {
				
				if ((target_env == 'dev-bifadmin1') || (target_env == 'qa-bifadmin1') || (target_env == 'uat-bifadmin1')){
					echo "promoting bifadmin1"
					deployComponents1(target_env, targets[target_env], "${artifactName}")
					//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")
				} else {
					echo "promoting bifadmin"
					deployComponents(target_env, targets[target_env], "${artifactName}")
				}
				
			} else {
				echo "select promote to deploy"
			}

		}
		
		
currentBuild.result = 'SUCCESS'
	} catch (exc) {

			currentBuild.result = 'FAILURE'


	} finally {
		stage('Notification'){
			echo "${currentBuild.result}"
			sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)
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
	remoteArtifact = "${artifactId}.${artExtension}"

	echo "local artifact=  ${localArtifact}"
	echo "remote artifact=  ${remoteArtifact}"
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
	echo "instance log ${instanceLogs}"
	echo "service name ${serviceName}"
	sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${artifactDeploymentLoc}/${artifactName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${artifactDeploymentLoc}/${artifactName}'
			scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:$artifactDeploymentLoc/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R tcserver:pivotal ${artifactDeploymentLoc}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service $serviceName start'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service $serviceName status'
		"""
	}
}

def deployComponents1(envTarget, targets, Artifact){

	echo "my env= ${envTarget}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { deploy1(it, artifactDeploymentLoc1, Artifact) }]
	}
	parallel stepsInParallel

}

def deploy1(target_hostname, artifactDeploymentLoc1, Artifact) {

	echo " the target is: ${target_hostname}"
	echo "instance log ${instanceLogs1}"
	echo "service name ${serviceName1}"
	sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs1}/tcserver.pid" ]; then /sbin/service ${serviceName1} stop; fi'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${artifactDeploymentLoc1}/${artifactName}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${artifactDeploymentLoc1}/${artifactName}'
			scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:$artifactDeploymentLoc1/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R tcserver:pivotal ${artifactDeploymentLoc1}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service $serviceName1 start'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service $serviceName1 status'
		"""
	}
}

//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} /sbin/service $serviceName1 start
//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs1}/tcserver.pid" ]; then /sbin/service ${serviceName1} stop; fi'

//def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

//	def stepsInParallel =  targets.collectEntries {
//		[ "$it" : { md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
//	}
//	parallel stepsInParallel

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
