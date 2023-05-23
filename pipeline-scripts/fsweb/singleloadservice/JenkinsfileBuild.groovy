import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/FSWEB-SingleLoadService-BE.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"
credsName = "scm_deployment"
maven="/opt/apache-maven-3.2.1/bin/mvn"
deploymentLocation ="/opt/apps/singleloadservice"
serviceName="singleloadservice"
user="jboss"
group="jboss"
artifactId="singleloadservice"
artExtension="jar"
logfile="singleloadservice.log"
artifactVersion=""

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
runQualityGate = "${RUN_QUALITY_GATE}"
promoteArtifact="false"    // The deployment has been removed from the build via Jenkins job.  -Paul Aug 1


//General pipeline
emailDistribution="ppiermarini@incomm.com"

targets = [
    'dev':  ['10.42.19.244','10.42.19.245']
]

node('linux'){
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
	stage('Setting JDK') {
			jdk=tool name:"openjdk-11.0.7.10-0"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}	


        stage('Build') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
				pom = readMavenPom file: 'pom.xml'
				artifactVersion = pom.getVersion();
                springbuildrelease(userInput, gitBranch, maven)
            }
        }
	

        stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == 'true') {
                   qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }


        stage('Deployment') {
            echo "Evaluating the parameters for stage run: userInput=${userInput} should be Build or Promote, && promoteArtifact=${promoteArtifact} should be true"
            when ((userInput == 'Build' && promoteArtifact == 'true')) {
                echo "Starting deployment Stage, userInput:${userInput}, promoteArtifact: ${promoteArtifact}"
                    targetEnvironment = 'dev'
                    echo "Build always deploys to ${targetEnvironment}"
					dir('target'){
					sh "ls ${artifactId}*.${artExtension} > outFile"
					Artifact = readFile 'outFile'
					}
					echo "The Artifact is ${Artifact}"
		    sh "cp target/${artifactId}*.${artExtension} ." 
			
		    echo "Artifact p${Artifact}p"
				deployComponents(targetEnvironment, targets[targetEnvironment], "${artifactId}-${artifactVersion}.${artExtension}" )
            }
        }
        /*stage('QA Automation') {
            echo "Evaluating the parameters for stage run: userInput=${userInput} should not be Release && testSuite=${testSuite} should not be none && projectProperties has test parameters"
                        tests(testSuite, workspaceFolder, ReadyAPI_License, ReadyAPI_TestRunner, targetEnvironment, emailDistribution)
        }*/

currentBuild.result = 'SUCCESS'

	} catch (any) {


	} finally {

		if (currentBuild.result == "SUCCESS"){

			stage('Notification'){
				currentBuild.result = 'SUCCESS'
				sendEmail(emailDistribution, gitBranch, userInput )	
			}

		}else{

			stage('Notification'){
				currentBuild.result = 'FAILURE'
				sendEmail(emailDistribution, gitBranch, userInput )
				//echo 'ERROR:  '+ exc.toString()
				//throw exc
			}
		}
	}


} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def deployComponents(String envName, targets, Artifact) {
    echo "my env= ${envName}"
	echo "My Artifact ${Artifact}" 	
    def stepsInParallel = targets.collectEntries {
        ["$it" : { springdeploy(credsName, it, deploymentLocation, Artifact, serviceName, artifactId, logfile) }]
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
