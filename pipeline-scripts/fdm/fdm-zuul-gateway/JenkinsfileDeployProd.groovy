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

svnUrl="https://svn.incomm.com/svn/fsitRepos/Platforms/GC/lib/GreenCard/branches/${Branch}"
		 
userInput="Promote"
//targetEnv="${targetEnv}"
targetEnv="${target_env}"
testName="myTest"

targets = [
    'dev':  ['10.42.16.178'],
    'qa':   ['10.42.81.29'],
    'stg':   ['10'],	
	'prod': ['10.10.10.??'],
]
//sdagctpsaps02v.unx.incommtech.net 10.42.16.178
//sqagctpsaps02v.unx.incommtech.net  10.42.81.29
//ssgcaps98fv
//ssgcaps99fv



//emailDistribution="ppiermarini@incomm.com"
emailDistribution="Greencard-Dev@incomm.com ppiermarini@incomm.com dstovall@incomm.com"
//General pipeline 
DeployLocationmain="/srv/jboss-eap-6.3/modules/incomm/lib/main"
Deploylocationtmp="/srv/jboss-eap-6.3/standalone-JBOSS_APS/tmp"
Deploylocationdata="/srv/jboss-eap-6.3/standalone-JBOSS_APS/data"
Deploylocationprojects="/srv/incomm-aps/jboss/deploy/projects/"
artifactDeploymentLoc="null"
serviceName="fdm-vms-serial-load"


//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"



///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.uss.lib.greencard'
artifactId = 'uss-greencard-ivr'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = ''


//globals
userApprove="Welcome to the new Jenkinsfile"
envInput="null"
envlevel="null"
svnrevision="null"
relVersion="null"
sonarStatus="null"
serviceStatus="null"
artifactFolder="artifacts"

node('linux'){
	try { 
			
		stage('checkout'){
			cleanWs()
			//checkoutSVN(svnUrl)
			//export the module.xml file
			sh "mkdir ${artifactFolder}"
			dir('artifacts'){
				sh "svn export --force ${svnUrl}/module.xml ."
			}
		}
		
		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote'){

				dir('artifacts'){
					artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
					artifactName="${artifactId}-${artifactVersion}.${artExtension}"
					
					//using the artifact selection to pull the additional jars with wget
					 withCredentials([usernamePassword(credentialsId: 'Artifactory', passwordVariable: 'ArtPass', usernameVariable: 'ArtUser')]) {
						sh """
							groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1
							wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/uss-greencard-ejb/${artifactVersion}/uss-greencard-ejb-${artifactVersion}-client.jar || exit 1
							wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/uss-greencard-ejb/${artifactVersion}/uss-greencard-ejb-${artifactVersion}.jar || exit 1
							wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/uss-greencard-util/${artifactVersion}/uss-greencard-util-${artifactVersion}.jar || exit 1
							wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/uss-greencard-x95msg/${artifactVersion}/uss-greencard-x95msg-${artifactVersion}.jar || exit 1
							wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/uss-greencard-constants/${artifactVersion}/uss-greencard-constants-${artifactVersion}.jar || exit 1
						"""
					}
				}
				echo "selected version ${artifactVersion}"

	
//			groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1	
//wget --user "${ArtUser}" --password "${ArtPass}" -O ${artifactName} --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/ivr/${artifactVersion}/ivr-${artifactVersion}.war"
	
			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		//stash includes: files to unstash on the FCV server
	//	stash includes: 'artifacts/*', name: 'myArtifacts'

		stage('Deployment'){
			

			//	unstash 'myArtifacts'
				sh 'hostname && ls -ltr && ls -ltr artifacts/'
				echo "This is where we do a bunch of stuff"

				if (userInput == 'Promote'){
				sh "hostname && pwd && ls -ltr"
				
//ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname'
//ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'pwd && ls'

			//	deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}")
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
		echo "stopping jboss..."
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/srv/jboss-eap-6.3/bin/jboss_shutdown_aps.sh'
		echo "removing old files and cleaning tmp and data locations"
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationtmp}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationdata}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}*.deployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}*.undeployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}*.deployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}*.undeployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}/uss-greencard-ivr*.war'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-util*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-x95msg*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-constants*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-ejb*.jar'
		echo "copying the new files to ${DeployLocationmain}"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/uss-greencard-util-${artifactVersion}.jar root@${target_hostname}:${DeployLocationmain}
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/uss-greencard-x95msg-${artifactVersion}.jar root@${target_hostname}:${DeployLocationmain}
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/uss-greencard-constants-${artifactVersion}.jar root@${target_hostname}:${DeployLocationmain}
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/uss-greencard-ejb-${artifactVersion}-client.jar root@${target_hostname}:${DeployLocationmain}
		echo "copying files to ${Deploylocationprojects}"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/uss-greencard-ejb-${artifactVersion}.jar root@${target_hostname}:${Deploylocationprojects}
		echo "deploying ivr war without version in the name"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/uss-greencard-ivr-${artifactVersion}.war root@${target_hostname}:${Deploylocationprojects}/uss-greencard-ivr.war
		echo "copying module.xml to ${DeployLocationmain}"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${artifactFolder}/module.xml root@${target_hostname}:${DeployLocationmain}/module.xml
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/srv/jboss-eap-6.3/bin/jboss_startup_aps.sh'
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