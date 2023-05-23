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
credsName = "scm_deployment"
warfileVersion="20.0"

targets = [
    'stg':   ['10.41.5.28'],	
	'prod1-3': ['spgcaps01fv', 'spgcaps02fv', 'spgcaps03fv'],
	'prod4-6': ['spgcaps04fv', 'spgcaps05fv', 'spgcaps06fv'],
	'prod7-9': ['spgcaps07fv', 'spgcaps8fv', 'spgcaps9fv'],
	'prod10-12': ['spgcaps10fv', 'spgcaps11fv', 'spgcaps12fv'],
	'prod13-15': ['spgcaps13fv', 'spgcaps14fv', 'spgcaps15fv'],
	'prod16-18': ['spgcaps16fv', 'spgcaps17fv', 'spgcaps18fv'],
	'prod19-21': ['spgcaps19fv', 'spgcaps20fv', 'spgcaps21fv'],
	'prod22-23': ['spgcaps22fv', 'spgcaps23fv']
]
//sdagctpsaps02v.unx.incommtech.net 10.42.16.178
//sqagctpsaps02v.unx.incommtech.net  10.42.81.29
//ssgcaps98fv 10.41.5.28
//ssgcaps99fv  10.41.5.29


BRANCH="${env.Branch}"

emailDistribution="ppiermarini@incomm.com phariharan@incomm.com"
//emailDistribution="Greencard-Dev@incomm.com ppiermarini@incomm.com dstovall@incomm.com"
//General pipeline 
DeployLocationmain="/srv/jboss-eap-6.3/modules/incomm/lib/main"
Deploylocationtmp="/srv/jboss-eap-6.3/standalone-JBOSS_APS/tmp"
Deploylocationdata="/srv/jboss-eap-6.3/standalone-JBOSS_APS/data"
Deploylocationlog="/srv/jboss-eap-6.3/standalone-JBOSS_APS/log"
Deploylocationprojects="/srv/incomm-aps/jboss/deploy/projects/"
artifactDeploymentLoc="null"
serviceName=""

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

now = new Date()
tstamp = now.format("YYYY-MM-dd", TimeZone.getTimeZone('UTC'))


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
artifactFolder="source/${BRANCH}"
chmod="755"
user="jboss"
group="wheel"

node('master'){

	sh "/bin/hostname"
			cleanWs()
			//checkoutSVN(svnUrl)
			sh "whoami"
			sh "java -version"
			dir('source'){
				sh "/bin/svn co ${svnUrl}"
			}
			sh "ls -ltr"
			stash includes: '**/*', name: 'source'
}

node('linux'){
	try { 
	cleanWs()
	sh "/bin/hostname"
		unstash 'source'	
		sh "ls -ltr"
		stage('checkout'){
			//cleanWs()
			//checkoutSVN(svnUrl)
			sh "whoami"
			sh "java -version"
			//sh "/bin/svn co ${svnUrl}"
			//export the module.xml file
			//dir('artifacts'){
			//	sh "svn export --force ${svnUrl}/module.xml ."
			//}
		}
		sh "ls -ltr"
		sh"pwd"
		//select the artifact 
		stage('Get Artifact'){

		pom1 = readMavenPom file: "source/${BRANCH}/war/pom.xml"
		ivrVersion = pom1.getVersion();

		pom2 = readMavenPom file: "source/${BRANCH}/ejb/pom.xml"
		ejbVersion = pom2.getVersion();

		pom3 = readMavenPom file: "source/${BRANCH}/util/pom.xml"
		utilVersion = pom3.getVersion();

		pom4 = readMavenPom file: "source/${BRANCH}/x95msg/pom.xml"
		x95msgVersion = pom3.getVersion();

		pom5 = readMavenPom file: "source/${BRANCH}/constants/pom.xml"
		constantsVersion = pom5.getVersion();

		echo "ivr= $ivrVersion, ejb= $ejbVersion, util= $utilVersion, x95msg $x95msgVersion, constants= $constantsVersion"

			if (userInput == 'Promote'){
				// for IVR war
				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: ivrVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				//artifactName="${artifactId}-${ivrVersion}.${artExtension}"

				 //using the artifact selection to pull the additional jars with wget
				 withCredentials([usernamePassword(credentialsId: 'Artifactory', passwordVariable: 'ArtPass', usernameVariable: 'ArtUser')]) {
					sh """
						groupIdDir=\$(echo ${groupId} | sed 's/\\./\\//g') || exit 1
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/ejb/${ejbVersion}/ejb-${ejbVersion}-client.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/ejb/${ejbVersion}/ejb-${ejbVersion}.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/util/${utilVersion}/util-${utilVersion}.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/x95msg/${x95msgVersion}/x95msg-${x95msgVersion}.jar
						wget --user "${ArtUser}" --password "${ArtPass}" --no-check-cert --no-verbose http://maven.incomm.com/artifactory/repo/\$groupIdDir/constants/${constantsVersion}/constants-${constantsVersion}.jar
					"""
				}
			sh "ls -ltr"
			sh "pwd"

			} else {
				echo "not getting artifact during a release build"
			}
			
		}

	//stash includes: files to unstash on the FCV server
	//stash includes: 'artifacts/*', name: 'myArtifacts'

		stage('Deployment'){
			

			//	unstash 'myArtifacts'
				sh 'hostname && ls -ltr && ls -ltr source/'

				if (userInput == 'Promote'){
				sh "hostname && pwd && ls -ltr"

				deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}")
			//md5SumCheck(targets[targetEnv],artifactDeploymentLoc,"${artifactId}.${artExtension}","${artifactId}.${artExtension}")

				} else {
					echo "not deploying during a release build"
				}
			

		}
		
		stage('Testing'){

			if ((userInput == 'Build')||(userInput == 'Promote')){
				echo " there is really no testing going on at this time"
		//	smokeTesting(targetEnv, targets[targetEnv], testName)
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
	sshagent([credsName]) {
		sh """
			echo "stopping jboss..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f ${Deploylocationlog}/server.log ]; then sudo -u jboss /srv/jboss-eap-6.3/bin/jboss_shutdown_aps.sh; else echo stopped; fi'
			echo "removing old files and cleaning tmp and data locations"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f ${Deploylocationlog}/server.log.${tstamp} ]; then echo exists; else /bin/mv ${Deploylocationlog}/server.log ${Deploylocationlog}/server.log.${tstamp}; fi'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationtmp}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationdata}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}*.deployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${Deploylocationprojects}*.undeployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}*.deployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}*.undeployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-util*.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-x95msg*.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-constants*.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${DeployLocationmain}/uss-greencard-ejb*.jar'
			echo "copying the new files to ${DeployLocationmain}"
			scp -q -o StrictHostKeyChecking=no util-${utilVersion}.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-util-${utilVersion}.jar
			scp -q -o StrictHostKeyChecking=no x95msg-${x95msgVersion}.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-x95msg-${x95msgVersion}.jar
			scp -q -o StrictHostKeyChecking=no constants-${constantsVersion}.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-constants-${constantsVersion}.jar
			scp -q -o StrictHostKeyChecking=no ejb-${ejbVersion}-client.jar root@${target_hostname}:${DeployLocationmain}/${prepend}-ejb-${ejbVersion}-client.jar
			echo "copying files to ${Deploylocationprojects}"
			scp -q -o StrictHostKeyChecking=no ejb-${ejbVersion}.jar root@${target_hostname}:${Deploylocationprojects}/${prepend}-ejb-${ejbVersion}.jar
			echo "copying module.xml to ${DeployLocationmain}"
			scp -q -o StrictHostKeyChecking=no ${artifactFolder}/module.xml root@${target_hostname}:${DeployLocationmain}/module.xml
			echo "setting permissions"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${Deploylocationprojects}/uss-greencard-ejb-${ejbVersion}.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${DeployLocationmain}/uss-greencard-util-${utilVersion}.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${DeployLocationmain}/uss-greencard-x95msg-${x95msgVersion}.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${DeployLocationmain}/uss-greencard-constants-${constantsVersion}.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${DeployLocationmain}/uss-greencard-ejb-${ejbVersion}-client.jar'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${Deploylocationprojects}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${DeployLocationmain}/'
			echo "starting jboss"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sudo -u jboss /srv/jboss-eap-6.3/bin/jboss_startup_aps.sh'
		"""
	}
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
	}
	parallel stepsInParallel

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