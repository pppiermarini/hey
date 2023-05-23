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


artifactDeploymentLoc ="D\$\\jboss-eap-6.3\\modules\\incomm\\properties\\main"
tmpLocation="D\$\\jboss-eap-6.3\\standalone\\tmp"
dataLocation="D\$\\jboss-eap-6.3\\standalone\\data"
serviceName="hello-world"

pipeline_id="${env.BUILD_TAG}"
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"

remoteArtifact="null"



///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'hello-world-testing'
artifactId = 'hello-world'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''

//globals
relVersion="null"



//userInput = InputAction()

node('windows'){
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
			
			// prelimEnv(target_env, relVersion)  If needed
			deployComponents(target_env, targets[target_env], "${artifactId}-${relVersion}.${artExtension}")
			md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")
		}
		
		stage('Testing'){
			// TBD
			smokeTesting(target_env, targets[target_env], testName)
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

def prelimEnv(target_env, relVersion){
	
	// prelim staging if needed
	
	echo "${target_env}"
	echo "Selected=  ${artifactId}-${relVersion}.${artExtension}"
	echo "DEPLOYING TO ${target_env}"
	echo "relVersion= ${relVersion}"
	writeFile file: "relVersion.txt", text: relVersion
//	bat "copy /Y relVersion.txt $pipelineData\\${target_env}"
	echo "DEPLOYING TO ${target_env}"

	localArtifact="${artifactId}-${relVersion}.${artExtension}"
	remoteArtifact="${artifactId}-${relVersion}.${artExtension}"
	
	echo " local artifact=  $localArtifact"
	echo "remote artifact=  $remoteArtifact"	
}


def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"

		targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact)
		}
		
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"
	bat """
		dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
		copy ${Artifact} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
		
		
	"""
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

		targets.each {
		println "Item: $it"
		md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact)
		}
}


def md5(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){
	
	def validate2 = md5w(targets, artifactDeploymentLoc, remoteArtifact, localArtifact)
	echo "validate2=  $validate2"
		if("${validate2}" != "0"){
		echo "${localArtifact} files are different 1"
		currentBuild.result = 'ABORTED'
		error('Files do not match...')
		}else{
		echo "${localArtifact} files are the same 0"
		}
}


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
	
		targets.each {
		println "Item: $it"
		tests(it, envName, testName)
		}

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
