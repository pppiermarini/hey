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


//globals

currentBuild.result = "SUCCESS"
emailDistribution="rkale@incomm.com "

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

loc = "e\$\\jenkins-home\\workspace\\Tests\\conditionalTest"

node('windows'){
	try { 
		cleanWs()
		//select the artifact
		stage('Testing Windows PS Script'){
				testSyntax()
		}

	} catch (exc) {

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


def testSyntax() {
powershell label: 'tests', script: 'if((get-process "outlook" -ea SilentlyContinue) -eq $Null) { dir } else { "Running"}'
}
///// The End