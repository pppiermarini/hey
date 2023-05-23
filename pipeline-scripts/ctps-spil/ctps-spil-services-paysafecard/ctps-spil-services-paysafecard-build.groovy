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


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-paysafecard.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
//folder="ear"


emailDistribution="vpilli@incomm.com rkale@incomm.com vhari@incomm.com mpalve@incomm.com ppiermarini@incomm.com psubramanian@incomm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
//Same Maven version from Maven Release Project
//maven="/opt/apache-maven-3.3.9/bin/mvn"
//maven="/opt/apache-maven-3.5.0/bin/mvn"
maven="/opt/apache-maven-3.6.3/bin/mvn"

imageName = "SPIL_Paysafe_Services"


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

		stage('Build-Release'){

			if (userInput == 'Build'){

				echo "Building a Snapshot Image to Maven Artifactory"
				
				//sh "${maven} clean deploy -f pom.xml -DskipTests=true"
				sh "${maven} -B -f pom.xml -U -X clean compile jib:build"
				}
			else if (userInput == 'Release'){
				//@TODO: Ask Team if there is a branch to specify for Releases 
				//gitBranch = ""
				//githubCheckout(gitCreds,gitRepository,gitBranch)

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
      			String[] tag;
      			tag = mavenReleaseVersion.split('-');
				image_tag = "${tag[0]}"

				echo "Docker Release for tag: ${image_tag}"
				//sh "${maven} -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true -DcreateChecksum=false' -B clean build-helper:parse-version org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -DdevelopmentVersion=${mavenReleaseVersion}"
				sh "${maven} clean compile com.google.cloud.tools:jib-maven-plugin:2.0.0:build -Dimage=docker.maven.incomm.com/${imageName}:${image_tag}"
				sh "${maven} compile jib:build"
				sh "git tag ${image_tag} && git push origin ${image_tag}"

				
			} else { echo "no build" }
			
		}//stage

	} //try 
		
	catch (any) {
		echo "Muy Mal"
		currentBuild.currentResult  = 'FAILURE'

	} finally {
	
	if (currentBuild.currentResult  == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.currentResult  == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.currentResult}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		}
	}
		
}  //end of node


///// The End
