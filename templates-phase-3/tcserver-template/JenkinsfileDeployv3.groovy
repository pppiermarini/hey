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


userInput="Promote"
//targetEnv="${targetEnv}"
targetEnv="${target_env}"
testName="myTest"

targets = [
    'dev':  ['10.42.18.219'],
    'qa':   ['10.42.18.21?'],
]

emailDistribution="ppiermarini@incomm.com"
//General pipeline 

artifactDeploymentLoc ="/srv/fdm/gateway/"
serviceName="fdm-zuul-gateway"

pipeline_id="${env.BUILD_TAG}"


//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"



///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.fdm'
artifactId = 'fdm-zuul-gateway'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'fdm-zuul-gateway.jar'


//globals
relVersion="null"



node('linux'){
	try { 
			

		//select the artifact 
		stage('Get Artifact'){

			
			if (userInput == 'Promote'){
				
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				artifactName="${artifactId}-${artifactVersion}.${artExtension}"
			
			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}")
			md5SumCheck(targets[targetEnv],artifactDeploymentLoc,"${artifactId}.${artExtension}","${artifactId}.${artExtension}")

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

def prelimEnv(targetEnv, relVersion){
	
	// prelim staging if needed
	
	echo "${targetEnv}"
	echo "Selected=  ${artifactId}-${relVersion}.${artExtension}"
	echo "DEPLOYING TO ${targetEnv}"
	echo "relVersion= ${relVersion}"
	writeFile file: "relVersion.txt", text: relVersion

	echo "DEPLOYING TO ${targetEnv}"

	localArtifact="${artifactId}-${relVersion}.${artExtension}"
	remoteArtifact="${artifactId}-${relVersion}.${artExtension}"
	
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


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"
	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv ${artifactDeploymentLoc}/${artifactId}.${artExtension}'
		scp -i ~/.ssh/pipeline ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/${artifactId}.${artExtension}
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 ${artifactDeploymentLoc}/${artifactId}.${artExtension}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
	"""
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
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
	
	relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"
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