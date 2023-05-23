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



gitRepository="https://github.com/InComm-Software-Development/ctps-aps-lib-apsutil"
gitBranch="${Branch}"
gitCreds="scm-incomm"


gitBranchDefault="origin/master"

// inputs from build with parameters
testName="myTest"

//vhari@incomm.com
emailDistribution="rkale@incomm.com "

versionCheck = Version.toBoolean()


pipeline_id="${env.BUILD_TAG}"

//Add java 8 bin
java=""

//Add java 11 bin
java11=""

currentBuild.result = 'SUCCESS'

node('linux'){
	try { 
		cleanWs()
		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('JDK Version Test'){
			if (versionCheck == true) {
				githubCheckout(gitCreds,gitRepository,gitBranchDefault)
				echo "JDK version 8"

			}
			else {
				githubCheckout(gitCreds,gitRepository,gitBranch)
				echo "JDK version 11"

			}
		}
		
	} catch (any) {
		echo "Muy Mal"
		
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



///// The End