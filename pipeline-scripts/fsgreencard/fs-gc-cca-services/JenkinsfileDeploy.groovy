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

credsName = 'scm_deployment'

svnUrl="https://svn.incomm.com/svn/fsitRepos/Platforms/GC/project/GreenCard-WebService/branches/${Branch}"
userInput="Promote"
targetEnv="${target_env}"
testName="myTest"

targets = [
    'dev':  ['10.42.19.202'],
    'qa':   ['10.42.83.122'],
]
//sdlgcccaapp01v.unx.incommtech.net 10.42.19.202
//sqlgcccaapp01v.unx.incommtech.net 10.42.83.122



emailDistribution="ppiermarini@incomm.com"
//General pipeline 

DeployLocationShared="/srv/incomm-gcwebservice/jboss/deploy/shared"
//DeployLocationmain="/srv/jboss-eap-6.3/modules/incomm/lib/main"
Deploylocationtmp="/srv/jboss-eap-6.3/standalone_gcwebservice/tmp"
Deploylocationdata="/srv/jboss-eap-6.3/standalone_gcwebservice/data"
//Deploylocationprojects="/srv/incomm-aps/jboss/deploy/projects/"
artifactDeploymentLoc="null"

JBossStartup="/srv/jboss-eap-6.3/bin/jboss_startup_cca.sh"
//JBossShutdown="/srv/jboss-eap-6.3/bin/jboss_shutdown_cca.sh"
JBossShutdown="/srv/jboss-eap-6.3/bin/pipeline_shutdown_jboss.sh"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"


//Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.greencard'
artifactId = 'gcservice'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = ''

//gcservice-0.1.war



node('linux'){
	try { 
			
		stage('checkout'){
			cleanWs()
			//checkoutSVN(svnUrl)
			//export the module.xml file
			//sh "svn export --force ${svnUrl}/module.xml ."
		}
		
		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote'){
				
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				artifactName="${artifactId}-${artifactVersion}.${artExtension}"

				echo "selected version ${artifactName}"

	
//			groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1	
//wget --user "${ArtUser}" --password "${ArtPass}" -O ${artifactName} --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/ivr/${artifactVersion}/ivr-${artifactVersion}.war"
	
			} else {
				echo "not getting artifact during a release build"
			}
			
		}


		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			//md5SumCheck(targets[targetEnv],artifactDeploymentLoc,"${artifactName},"${artifactName}")

			} else {
				echo "not deploying during a release build"
			}

		}
		
		stage('Testing'){

			if ((userInput == 'Build')||(userInput == 'Promote')){
			smokeTesting(targetEnv, targets[targetEnv], testName)
			} else {
				echo "not testing during a release build"
			}

		}

		
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		notifyBuild(emailDistribution)
		//sendEmail(emailDistribution, userInput, gitBranch) 
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def prelimEnv(targetEnv, artifactVersion){
	
	// prelim staging if needed
	
	echo "${targetEnv}"
	echo "Selected=  ${artifactId}-${artifactVersion}.${artExtension}"
	echo "DEPLOYING TO ${targetEnv}"

	localArtifact="${artifactId}-${artifactVersion}.${artExtension}"
	remoteArtifact="${artifactId}-${artifactVersion}.${artExtension}"
	
	echo " local artifact=  $localArtifact"
	echo "remote artifact=  $remoteArtifact"	
}


def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	}
	parallel stepsInParallel
	
}

// 		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${JBossShutdown}'
// 		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${JBossStartup}'


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"
	
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${JBossShutdown} > /dev/null'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationtmp}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationdata}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationShared}*.deployed'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationShared}*.undeployed'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationShared}/gcservice*.war'
		scp -q -o StrictHostKeyChecking=no gcservice-${artifactVersion}.${artExtension} root@${target_hostname}:${DeployLocationShared}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${JBossStartup}'
	"""
	}
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { md5w(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
	}
	parallel stepsInParallel

}


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
//	if (userInput == "Promote"){
	echo "Select an artifact from Artifacory"
	
	artifactVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	
	echo "Selected Artifact=  ${artifactId}-${artifactVersion}.${artExtension}"
//	} else {
//	echo "not promoting-Skipping"
//	}	
}

def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
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

def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End