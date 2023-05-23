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

svnUrl="https://svn.incomm.com/svn/fsitRepos/Platforms/GC/lib/GreenCard/branches/${Branch}"
		 
userInput="Promote"
//targetEnv="${targetEnv}"
targetEnv="${target_env}"
testName="myTest"

warfileVersion="20.0"

targets = [
    'dev':  ['10.42.16.178'],
    'qa':   ['10.42.81.29'],
	'prod': ['10.10.10.10'],
]
//Env.Names
//sdagctpsaps02v.unx.incommtech.net 10.42.16.178
//sqagctpsaps02v.unx.incommtech.net  10.42.81.29



emailDistribution="Greencard-Dev@incomm.com ppiermarini@incomm.com"
//General pipeline 
DeployLocationmain="/srv/jboss-eap-7.2/modules/incomm/lib/main"
Deploylocationprojects="/srv/incomm-aps/jboss/deploy/projects"
Deploylocationtmp="/srv/jboss-eap-7.2/standalone-JBOSS_APS/tmp"
Deploylocationdata="/srv/jboss-eap-7.2/standalone-JBOSS_APS/data"
artifactDeploymentLoc="null"
serviceName="fdm-vms-serial-load"

JbossBin="/srv/jboss-eap-7.2/bin"
//fyi previous jboss was 6.3

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"


///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.uss.lib.greencard'
artifactId = 'uss-greencard-ivr'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = ''

prepend="uss-greencard"

//globals
userApprove="Welcome to the new Jenkinsfile"
envInput="null"
envlevel="null"
svnrevision="null"
relVersion="null"
sonarStatus="null"
serviceStatus="null"
chmod="755"
user=""
group=""


node('linux'){
	currentBuild.result="SUCCESS"
	try { 
			
		stage('checkout'){
			cleanWs()
			checkoutSVN(svnUrl)
			//export the module.xml file
			sh "svn export --force ${svnUrl}/module.xml ."
		}
		
		//select the artifact 
		stage('Get Artifact'){

		pom1 = readMavenPom file: 'war/pom.xml'
		ivrVersion = pom1.getVersion();
		
		pom2 = readMavenPom file: 'ejb/pom.xml'
		ejbVersion = pom2.getVersion();
		
		pom3 = readMavenPom file: 'util/pom.xml'
		utilVersion = pom3.getVersion();
		
		pom4 = readMavenPom file: 'x95msg/pom.xml'
		x95msgVersion = pom3.getVersion();
		
		pom5 = readMavenPom file: 'constants/pom.xml'
		constantsVersion = pom5.getVersion();
		
		echo "ivr= $ivrVersion, ejb= $ejbVersion, util= $utilVersion, x95msg $x95msgVersion, constants= $constantsVersion"
		
			if (userInput == 'Promote'){
				
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: ivrVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				artifactName="${artifactId}-${ivrVersion}.${artExtension}"
				
				 //using the artifact selection to pull the additional jars with wget
				 withCredentials([usernamePassword(credentialsId: 'maven_read_only', passwordVariable: 'ArtPass', usernameVariable: 'ArtUser')]) {
					sh """
						groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/ejb/${ejbVersion}/ejb-${ejbVersion}-client.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/ejb/${ejbVersion}/ejb-${ejbVersion}.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/util/${utilVersion}/util-${utilVersion}.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/x95msg/${x95msgVersion}/x95msg-${x95msgVersion}.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/constants/${constantsVersion}/constants-${constantsVersion}.jar
					"""
				}

	
			} else {
				echo "not getting artifact during a release build"
			}
			
		}


		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){

			deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}")

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
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '${JbossBin}/jboss_shutdown_aps.sh'
		echo "removing old files and cleaning tmp and data locations"
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationtmp}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationdata}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}*.deployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}*.undeployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}*.deployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}*.undeployed'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}/uss-greencard-ivr*.war'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}/uss-greencard-ejb*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-util*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-x95msg*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-constants*.jar'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-ejb*.jar'
		echo "copying the new files to ${DeployLocationmain}"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no util-${utilVersion}.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-util-${utilVersion}.jar
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no x95msg-${x95msgVersion}.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-x95msg-${x95msgVersion}.jar
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no constants-${constantsVersion}.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-constants-${constantsVersion}.jar
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ejb-${ejbVersion}-client.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-ejb-${ejbVersion}-client.jar
		echo "copying files to ${Deploylocationprojects}"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ejb-${ejbVersion}.jar root@${target_hostname}:${Deploylocationprojects}/${prepend}-ejb-${ejbVersion}.jar
		echo "deploying ivr war without version in the name"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no ${prepend}-ivr-${ivrVersion}.war root@${target_hostname}:${Deploylocationprojects}/
		echo "copying module.xml to ${DeployLocationmain}"
		scp -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no module.xml root@${target_hostname}:${DeployLocationmain}/module.xml
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${DeployLocationmain}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '${JbossBin}/jboss_startup_aps.sh'
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