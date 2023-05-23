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


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-google.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


testName="myTest"
//folder="ear"


emailDistribution="rkale@incomm.com vhari@incomm.com ppiermarini@incomm.com dstovall@InComm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
//Same Maven version from Maven Release Project
//maven="/opt/apache-maven-3.3.9/bin/mvn"
//maven="/opt/apache-maven-3.5.0/bin/mvn"
maven="/opt/apache-maven-3.6.3/bin/mvn"

imageName = "spl-services-google"


node('docker'){
	try { 
		cleanWs()

		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}
		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}//stage

		stage('Local-Tarball-Image'){

			

				echo "Building docker image tarball on local workspace"
				
				//jib:buildTar saves the images locally on the workspace
				sh "${maven} -B -f pom.xml -U -X clean compile jib:buildTar"
		
			
		}//stage

	} //try 
		
	catch (any) {
		echo "Muy Mal"
		currentBuild.result  = 'FAILURE'

	} finally {
	
	if (currentBuild.result  == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result  == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		}
	}
		
}  //end of node


///// The End
