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

svnUrl="https://svn.incomm.com/svn/fsitRepos/Platforms/GC/project/GreenCard-FileProcessor/branches/${Branch}"
userInput="Promote"
targetEnv="${target_env}"
testName="myTest"

targets = [
    'dev':  ['10.42.16.23'],
]



//emailDistribution="ppiermarini@incomm.com"
emailDistribution="ppiermarini@incomm.com Greencard-Dev@incomm.com dstovall@incomm.com"
//General pipeline 

artifactDeploymentLoc ="D\$\\FBP"



//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"


//Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.greencard.fbp'
artifactId = 'fbp'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''
classifier='jar-with-dependencies'

groupIdDir="com/incomm/greencard/fbp"



node('windows'){
	try { 
			
		stage('checkout'){
			cleanWs()
			checkoutSVN(svnUrl)
			//export the module.xml file
			//sh "svn export --force ${svnUrl}/module.xml ."
		}
		
		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote'){
				
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				artifactName="${artifactId}-${artifactVersion}-${classifier}.${artExtension}"

				echo "selected Artifact version ${artifactName}"
				bat "set WGETRC=E:\\jenkins-tools\\wget\\wgetrc && E:\\jenkins-tools\\wget\\wget.exe --no-check-certificate https://maven.incomm.com/artifactory/repo/${groupIdDir}/${artifactId}/${artifactVersion}/${artifactName}"

			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		now = new Date()
		folder = now.format("YYYYMMdd-HHmmss")

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			//md5SumCheck(targets[targetEnv],Deploylocationprojects,"${artifactName},"${artifactName}")

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
	
		targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact)
		}
	
}

// 		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${JBossShutdown}'
// 		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '${JBossStartup}'


def deploy(target_hostname, DeployLocationmain, Artifact) {

	echo " the target is: ${target_hostname}"
	bat """
		mkdir \\\\${target_hostname}\\${artifactDeploymentLoc}\\backup\\${folder}
		copy \\\\${target_hostname}\\${artifactDeploymentLoc}\\${artifactId}*-${classifier}.${artExtension} \\\\${target_hostname}\\${artifactDeploymentLoc}\\backup\\${folder}
		dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
		dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\backup\\${folder}
		del \\\\${target_hostname}\\${artifactDeploymentLoc}\\${artifactId}*-${classifier}.${artExtension}
		copy ${artifactName} \\\\${target_hostname}\\${artifactDeploymentLoc}\\
	"""
}


def md5SumCheck(targets, DeployLocationmain, remoteArtifact, localArtifact){

	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { md5w(it, DeployLocationmain, remoteArtifact, localArtifact) } ]
	}
	parallel stepsInParallel

}


//def md5(targets, Deploylocationprojects, remoteArtifact, localArtifact){
	
//	def validate2 = md5(targets, Deploylocationprojects, remoteArtifact, localArtifact)
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