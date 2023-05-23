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
// 'int': ['10.42.32.205','10.42.32.206'],
//,'10.42.80.27'
targets = [
    'dev':  ['10.42.17.63','10.42.17.64'],
    'qa':   ['10.42.80.26'],
    'uat': ['10.42.48.149','10.42.49.201']
]
   // Old QA servers'qa':   ['10.42.84.112','10.42.84.113'],
   

//General pipeline
emailDistribution="atiruveedhi@incomm.com rkale@incomm.com"


//Given by Abhilash T.
artifactDeploymentLocOne = "/opt/cachedaemon/cachedaemon-3.1"
artifactDeploymentLocTwo = "/opt/cachedaemon/cachedaemon-3.1/lib"

serviceName="cachedaemon"
pipeline_id="${env.BUILD_TAG}"

user="root"
group="root"
//@TODO: Change this
chmod="644"
chmod_sh="755"

chmod_config="664"
//Given by Abhilash T.
configFolderLoc="/opt/cachedaemon/cachedaemon-3.1/instances/"


gitRepository="https://github.com/InComm-Software-Development/mdm-cache-daemon.git"
gitTag="${TAG}"
gitCreds="scm-incomm"
folder="configuration"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"



//Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.cache'
artifactId = 'cachedaemon'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'cachedaemon'
classifier = "dist"
extension = 'tar.gz'

//globals

relVersion="null"

artifactVersion="${artifactVersion}"

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

//currentBuild.result = "SUCCESS"

finalVersion = ''



node('linux'){
	try { 
		cleanWs()			
		//select the artifact 
		stage('Get Artifact'){

			
			if (userInput == 'Promote'){
				githubCheckout(gitCreds,gitRepository,gitTag)
				pom = readMavenPom file: 'pom.xml'
				pom_version = pom.version
				echo "Printing out the pom version"
				String[] versionsplit = pom_version.split("-")	
				finalVersion = versionsplit[0]
				echo "${finalVersion}"				
				artifactResolver artifacts: [artifact(artifactId: artifactId, classifier: classifier, extension: extension, groupId: groupId, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
				//artifactResolver artifacts: [artifact(artifactId: artifactId, classifier: classifier, extension: extension, groupId: groupId, version: env_propertyName)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'

				//echo "${artifactId}-${artifactVersion}.${artExtension}"
			sh """
				ls -ltr
				tar -xvf ${artifactName}-${artifactVersion}-dist.tar.gz
				ls -ltr
				"""
			
			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}", "${finalVersion}")
			
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


def deployComponents(envName, targets, Artifact, Version){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLocOne, artifactDeploymentLocTwo, Artifact, envName, Version) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLocOne,artifactDeploymentLocTwo, Artifact, envName, Version) {
	/*
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -r ${configFolderLoc}/ config_${gitDeployModule}_${artifactVersion}_${nowFormatted}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mv ${artifactDeploymentLoc}/config_* ${archiveFolderLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${configFolderLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLocOne}/${artifactId}-${Version}.${artExtension}
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLocTwo}/${artifactId}-${Version}.${artExtension}



	*/
	echo " the target is: ${target_hostname}"
	//@TODO: Ask the current version that needs to get appended to the artifact
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'

		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLocOne}/${artifactId}*.jar'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLocTwo}/*.jar'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLocOne}/*.sh'
		scp -o StrictHostKeyChecking=no -r ${artifactName}-${artifactVersion}/${artifactName}-${artifactVersion}.${artExtension} root@${target_hostname}:${artifactDeploymentLocOne}/${artifactId}-${artifactVersion}.${artExtension}
		scp -o StrictHostKeyChecking=no -r ${artifactName}-${artifactVersion}/lib/hazelcast*.jar root@${target_hostname}:${artifactDeploymentLocTwo}/
		scp -o StrictHostKeyChecking=no -r ${artifactName}-${artifactVersion}/lib/log4j*.jar root@${target_hostname}:${artifactDeploymentLocTwo}/
		scp -o StrictHostKeyChecking=no -r ${artifactName}-${artifactVersion}/${artifactName}-${artifactVersion}.${artExtension} root@${target_hostname}:${artifactDeploymentLocTwo}/${artifactId}-${artifactVersion}.${artExtension}
		scp -o StrictHostKeyChecking=no -r ${artifactName}-${artifactVersion}/${artifactId}.sh root@${target_hostname}:${artifactDeploymentLocOne}
		scp -o StrictHostKeyChecking=no -r ${artifactName}-${artifactVersion}/init.d.sh root@${target_hostname}:${artifactDeploymentLocOne}
		scp -o StrictHostKeyChecking=no -r ${folder}/${envName}/* root@${target_hostname}:${configFolderLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLocOne}/${artifactId}-${artifactVersion}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod_sh} ${artifactDeploymentLocOne}/*.sh'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLocTwo}/*'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod_config} ${configFolderLoc}/mdm.xml'		
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${configFolderLoc}/mdm.xml'

		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
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