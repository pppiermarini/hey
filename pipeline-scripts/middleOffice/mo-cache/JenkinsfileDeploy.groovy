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

credsName = "scm_deployment"
userInput="Promote"
//targetEnv="${targetEnv}"
//targetEnv="${target_Env}"
testName="myTest"


targets = [
    'dev':  ['10.42.19.122'],
    'qa':   ['10.42.18.147'],

]
   // Old QA servers'qa':   ['10.42.84.112','10.42.84.113'],
   

//General pipeline
//tmalik@incomm.com sganta@incomm.com vhari@incomm.com
emailDistribution="ratnoorkar@incomm.com rkale@incomm.com tmalik@incomm.com sganta@incomm.com vhari@incomm.com"


/*
* The below configs are taken from the existing pipeline: https://svn.incomm.com/svn/scm/pipeline_scripts/mdm/mdm_api/Jenkinsfilep2
*/

//Given by Rajiv A.
artifactconfigDeploymentLoc ="/var/opt/mo-cache-app"

serviceName="mo-cache.service"
pipeline_id="${env.BUILD_TAG}"

user="deploy"
group="middleoffice"
chmod="755"


//Given by Abhilash T.
//configFolderLoc="/var/opt/pivotal/pivotal-tc-server-standard/mdm-ws-instance/lib/"


gitRepository="https://github.com/InComm-Software-Development/ipbr-configs-mo-cache-loader.git"
gitBranch="${BRANCH}"
gitCreds="scm-incomm"
gitDeployConfig="mo-cache.conf"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"



//Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.middleoffice'
artifactId = 'mo-cache-rest-api'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'mo-cache.jar'
warFolderName = "mo-cache-app"

//globals

relVersion="null"

artifactVersion="${artifactVersion}"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

currentBuild.result = 'SUCCESS'


node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage('Get Artifact'){

			
			if (userInput == 'Promote'){
				githubCheckout(gitCreds,gitRepository,gitBranch)
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

				echo "${artifactId}-${artifactVersion}.${artExtension}"
				sh "ls -ltr"
			
			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			
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
			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
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
		[ "$it" : { deploy(it, artifactconfigDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactconfigDeploymentLoc, Artifact, envName) {
	/*
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -r ${configFolderLoc}/ config_${gitDeployModule}_${artifactVersion}_${nowFormatted}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mv ${artifactDeploymentLoc}/config_* ${archiveFolderLoc}/'
	*/
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl stop ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactconfigDeploymentLoc}/${artifactName}*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactconfigDeploymentLoc}/
		scp -o StrictHostKeyChecking=no ${gitDeployConfig} root@${target_hostname}:${artifactconfigDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactconfigDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactconfigDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactconfigDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactconfigDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactconfigDeploymentLoc}/'
		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl start ${serviceName}'
		"""
	}

}

def getFromArtifactory(){
//	if (userInput == "Promote"){
	echo "Select an artifact from Artifactory"
	
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