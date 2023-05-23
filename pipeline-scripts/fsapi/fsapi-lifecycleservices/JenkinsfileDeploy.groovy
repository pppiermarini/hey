import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


//gitRepository="https://github.com/InComm-Software-Development/FSAPI-LifeCycleServices.git"
//gitBranch="${Branch}"
//gitCreds="scm-incomm"


credsName = "scm_deployment"
userInput="Promote"

targetEnv="qa"
targets = [
	'qa':   ['10.42.81.139'],
]

//emailDistribution="ppiermarini@incomm.com ssanka@incomm.com dkumar@incomm.com ajapa@incomm.com"
emailDistribution="ppiermarini@incomm.com"

//General pipeline
artifactDeploymentLoc ="/opt/jboss/standalone-lcs/deployments"
propertiesDeploymentLoc="/opt/jboss/standalone-lcs/deployments/config"
commonProperties="src/main/resources/common"
srcProperties="src/main/resources/${targetEnv}"

serviceName="jboss-as-standalone-lcs.service"

pipeline_id="${env.BUILD_TAG}"

user="jboss"
group="jboss"
chmod="750"

testName="myTest"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"


///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.lifecycle'
artifactId = 'lifeCycleServices'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'lifeCycleServices.war'


//globals

relVersion="null"

//artifactVersion="${artifactVersion}"

node('linux1'){
	try { 

		//stage('checkout'){
		//	cleanWs()
		//	githubCheckout(gitCreds,gitRepository,gitBranch)
		//}

		//select the artifact 
		stage ('Get Artifact'){
    	     		// Select and download artifact
            		list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
            
           		 echo "the list contents ${list}"
            
			artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
            		parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
            		sleep(3)
            		artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
            		echo "the artifact version ${artifactVersion}"
			sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactId}.${artExtension}"
    			sh "ls -ltr"
    		}

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			//md5SumCheck(targets[targetEnv],artifactDeploymentLoc,"${artifactId}.${artExtension}","${artifactId}.${artExtension}")

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

		
	} catch (exc) {

			currentBuild.result = 'FAILURE'
			sendEmail(emailDistribution, userInput, targetEnv)
			echo 'ERROR:  '+ exc.toString()
			throw exc

		
	} finally {

			currentBuild.result = 'SUCCESS'
			sendEmail(emailDistribution, userInput, targetEnv)	

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

//		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${artifactId}.*'
def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl stop ${serviceName}'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLoc}/${Artifact}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/${Artifact}'
		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl restart ${serviceName}'
		"""
	}
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


///// The End
