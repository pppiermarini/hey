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
//targetEnv="${target_env}"
testName="myTest"


//sdfdmapp02v
//sqafdmapp01v,
//SQLAFSSBCWS03V (10.42.82.216)
//SQLAFSSBCWS04V (10.42.82.217)
//    'int': ['10.42.32.205','10.42.32.206'],
//    'uat': ['10.42.48.149','10.42.49.201']
//    'uat': ['10.42.48.149','10.42.49.201'],

targets = [
    'dev':  ['10.42.17.64', '10.42.17.65', '10.42.17.67'],
    'qa':   ['10.42.80.26','10.42.80.27'],
    'int1': ['10.42.32.205'],
    'int2': ['10.42.32.206'],
    'int': ['10.42.32.205','10.42.32.206']

]
   // Old QA servers'qa':   ['10.42.84.112','10.42.84.113'],
   

//General pipeline
//glodha@incomm.com vhari@incomm.com
emailDistribution="atiruveedhi@incomm.com rkale@incomm.com glodha@incomm.com vhari@incomm.com"

//emailCheck = Email.toBoolean()


//Given by Abhilash T.
artifactDeploymentLoc ="/var/opt/pivotal/pivotal-tc-server-standard/mdm-ui-instance/webapps"

instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/mdm-ui-instance/logs"


serviceName="mdm-ui-instance"
pipeline_id="${env.BUILD_TAG}"

user="tcserver"
group="pivotal"

chmod="755"


//Given by Abhilash T.
configFolderLoc="/var/opt/pivotal/pivotal-tc-server-standard/mdm-ui-instance/lib/"


gitRepository="https://github.com/InComm-Software-Development/mdm-configuration.git"
gitTag="${TAG_CONFIGURATION}"
gitCreds="scm-incomm"
gitDeployModule="mdm-ui-config"
gitDeployConfigModule="mdm-client-config"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"



//Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.mdm.webapp'
artifactId = 'mdm-webapp-ui'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'mdm-webapp-ui.war'
warFolderName = "mdm-webapp-ui"

//globals

relVersion="null"

artifactVersion="null"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

currentBuild.result = 'SUCCESS'


node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage('Get Artifact'){
			githubCheckout(gitCreds,gitRepository,gitTag)

			selectArtifact()
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
			echo "BEFORE RENAME: "
			sh "ls -tlar *.${artExtension}" 

			sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactId}.${artExtension}"

			echo "AFTER RENAME: "
			sh "ls -tlar *.${artExtension}" 

			
			// if (userInput == 'Promote'){
			// 	artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

			// 	echo "${artifactId}-${artifactVersion}.${artExtension}"
			// 	sh "ls -ltr"
			
			// } else {
			// 	echo "not getting artifact during a release build"
			// }
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
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - Artifact Version: ${artifactVersion} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "The artifact Version deployed is: ${artifactVersion}"
		
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
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	/*
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -r ${configFolderLoc}/ config_${gitDeployModule}_${artifactVersion}_${nowFormatted}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mv ${artifactDeploymentLoc}/config_* ${archiveFolderLoc}/'
	*/
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${warFolderName}*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
		scp -o StrictHostKeyChecking=no -r common/${gitDeployModule}/* root@${target_hostname}:${configFolderLoc}"""

		if (("${envName}" == "int2") || ("${envName}" == "int1")) { 
		envName = "int"
		sh """ 
		scp -o StrictHostKeyChecking=no -r ${envName}/${gitDeployModule}/* root@${target_hostname}:${configFolderLoc}
		scp -o StrictHostKeyChecking=no -r ${envName}/${gitDeployConfigModule}/* root@${target_hostname}:${configFolderLoc}	"""
		}
		sh """
		scp -o StrictHostKeyChecking=no -r ${envName}/${gitDeployModule}/* root@${target_hostname}:${configFolderLoc}
		scp -o StrictHostKeyChecking=no -r ${envName}/${gitDeployConfigModule}/* root@${target_hostname}:${configFolderLoc}		
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${configFolderLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${configFolderLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'"""
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

def selectArtifact() {
	// Select and download artifact
	list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
	echo "the list contents ${list}"
	artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
	parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
	sleep(3)
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