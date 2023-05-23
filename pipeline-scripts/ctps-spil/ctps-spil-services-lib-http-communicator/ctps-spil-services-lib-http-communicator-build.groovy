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


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-lib-http-communicator.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

maven="/opt/apache-maven-3.2.1/bin/mvn"


// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
//folder="ear"


//softwaresolutions@incomm.com
emailDistribution="vpilli@incomm.com rkale@incomm.com vhari@incomm.com mpalve@incomm.com ppiermarini@incomm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
//Same Maven version from Maven Release Project
//maven="/opt/apache-maven-3.3.9/bin/mvn"
//maven="/opt/apache-maven-3.5.0/bin/mvn"

node('linux'){
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
				

				sh "${maven} -U clean deploy -Dmaven.test.failure.ignore=false"
				
				//sh "${maven} -U clean deploy -Dmaven.test.failure.ignore=false"
				//sh "${maven} -U clean deploy -f pom.xml -DskipTests"

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
		
				
				sh "${maven} -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true' -B clean build-helper:parse-version org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -DdevelopmentVersion=${mavenReleaseVersion}"

				//sh "${maven} versions:use-releases"
				prop = readProperties file: 'release.properties'
				echo "${prop}"
				
				sleep(4)

				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				
			} else { echo "no build" }
			
		}//stage

	} //try 
		
	catch (any) {
		echo "Muy Mal"
		//currentBuild.result  = 'FAILURE'

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
