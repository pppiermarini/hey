import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-web-ddc.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"
credsName = "scm_deployment"
maven="/opt/apache-maven-3.2.1/bin/mvn"


// inputs from build with parameters
userInput="Build"


//General pipeline
NOTIFICATION_LIST = "vchavva@incomm.com,ppiermarini@incomm.com"


node('linux'){
	
	try { 
	
currentBuild.result = 'SUCCESS'

		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}

		stage('checkout'){
			echo "JDK is ${jdkVersion}"
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}

		
        stage('Build') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build') {
               sh "${maven} -X clean -U deploy  org.jacoco:jacoco-maven-plugin:prepare-agent -Pcoverage-per-test"
            }
        }
/*
        stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build') {
                echo "Checking quality gate parameter: ${RUN_QUALITY_GATE}"
                if (RUN_QUALITY_GATE == "true") {
					echo "calling QG"
                  qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }
*/

	} catch (any) {
	
	currentBuild.currentResult  = 'FAILURE'

	} finally {
stage('Notification') {
			if(NOTIFICATION_LIST.trim().length() > 0) {
				echo "send notification that a new artifact has been uploaded"

				mail 	to:"${NOTIFICATION_LIST}",
					subject:"${JOB_NAME} ${BUILD_NUMBER}",
					body: """
A new build artifact has been created from the ${gitBranch} branch.

**************************************************
Build-Node: ${NODE_NAME}
Jenkins-Build-Number: ${BUILD_NUMBER}
Jenkins-Build-URL: ${BUILD_URL}
Build-Branch: ${gitBranch}
**************************************************\n\n\n
"""
					}
}
	}//finally


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



///// The End
