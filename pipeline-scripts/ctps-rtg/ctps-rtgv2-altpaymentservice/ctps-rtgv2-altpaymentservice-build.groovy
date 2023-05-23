import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/ctps-rtgv2-altpaymentservice.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
emailDistribution="shaque@incomm.com rkale@incomm.com vhari@incomm.com skeshari@InComm.com mmurarisetty@incomm.com stadoori@incomm.com"
pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.6.3/bin/mvn"
imageName = "ctps-rtgv2-altpaymentservice"
snykTokenId = "snyk-rtg"
artifactId = ""
artifactVersion = ""


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

		stage('snyk-scan') {
			pom = readMavenPom file: 'pom.xml'
			artifactId = pom.getArtifactId()
			artifactVersion = pom.getVersion()
			snykScanV2(snykTokenId, artifactId, artifactVersion)
		}

		stage('snyk-container-scan') {
			pom = readMavenPom file: 'pom.xml'
			imageName = pom.getArtifactId()
			imageTag = pom.getVersion()
			snykContainerScan(snykTokenId, imageName, imageTag)
		}

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
