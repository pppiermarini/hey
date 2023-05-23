import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

targetEnv="${target_env}"

//gitRepository="https://github.com/InComm-Software-Development/FSWEB-SingleLoadService-BE.git"
//gitBranch="${Branch}"
//gitCreds="scm-incomm"

credsName = "scm_deployment"
maven="/opt/apache-maven-3.2.1/bin/mvn"
deploymentLocation ="/opt/apps/activationservice" 
serviceName = "activationservice" 
user="jboss"
group="jboss"
artifactId="activation" 
artExtension="jar"
logfile="activationService.log" 


// inputs from build with parameters
userInput="Promote"

//General pipeline
emailDistribution="jrivett@incomm.com"

targets = [
	'dev':  ['10.42.19.242','10.42.19.243'],
    'uat':  ['10.42.81.101'], 
	'qa': ['10.42.81.105','10.42.81.106'] 
]


///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm' 
artifactId = 'activation' 
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''


//globals

relVersion="null"

artifactVersion="${artifactVersion}"
currentBuild.result = "SUCCESS"
node('linux'){
	try { 

		/*stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}*/

		//select the artifact 
		stage('Get Artifact'){


			if (userInput == 'Promote'){

				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)]

				echo "${artifactId}-${artifactVersion}.${artExtension}"
				artifactName = "${artifactId}-${artifactVersion}.${artExtension}"
				//sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactId}.${artExtension}"
				sh "ls -ltr"

			} else {
				echo "not getting artifact during a release build"
			}

		}

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){
			echo "Deployment commented out"
			deployComponents(targetEnv, targets[targetEnv], "${artifactName}" )
			//md5SumCheck(targets[targetEnv],deploymentLocation,"${artifactName}","${artifactName}")

			} else {
				echo "not deploying during a release build"
			}

		}

//		stage('Testing'){
//
//			if ((userInput == 'Build')||(userInput == 'Promote')){
//			smokeTesting(targetEnv, targets[targetEnv], testName)
//			} else {
//				echo "not testing during a release build"
//			}
//		}


	} catch (exc) {

			currentBuild.result = 'FAILURE'
			sendEmailv3(emailDistribution)
			echo 'ERROR:  '+ exc.toString()
			throw exc

	} finally {

			currentBuild.result = 'SUCCESS'
			sendEmailv3(emailDistribution)	

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
	echo "My Artifact ${Artifact}" 	
    def stepsInParallel = targets.collectEntries {
        ["$it" : { currentBuild.result = springdeploy(credsName, it, deploymentLocation, Artifact, serviceName, artifactId, logfile) }]
    }
    parallel stepsInParallel
}


def md5SumCheck(targets, artifactDeploymentLoc, remoteArtifact, localArtifact){

	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { md5(it, artifactDeploymentLoc, remoteArtifact, localArtifact) } ]
	}
	parallel stepsInParallel

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
