import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _



// inputs from build with parameters
//userInput="${BUILD_TYPE}"
testName="myTest"

//General pipeline
emailDistribution="rkale@incomm.com"



pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

currentBuild.result = 'SUCCESS'

jdkElevenBinary="openjdk-11.0.5.10.linux.tar.xz"

jdkElevenRenamed="openjdk-11.0.5.linux.tar.xz"

jdkUnatar="java-11-openjdk-11.0.5.10-1.static.jdk.openjdkportable.x86_64"

node('linux'){
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			sh """
			/bin/wget --no-check-cert https://maven.incomm.com/artifactory/scm/openjdk/${jdkElevenBinary}
			"""
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