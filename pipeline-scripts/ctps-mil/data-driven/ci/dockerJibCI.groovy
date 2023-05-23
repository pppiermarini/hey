import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


// inputs from build with parameters
gitRepository="${GIT_REPOSITORY}"
gitBranch="${BRANCH}"
userInput="${BUILD_TYPE}"
jdkVersion = "${JDK_VERSION}"

gitCreds="scm-incomm"
maven="/opt/apache-maven-3.3.9/bin/mvn"
snykTokenId = "snyk-rtg"
artifactId = ""
artifactVersion = ""

emailDistribution="dstovall@incomm.com jrivett@incomm.com shaque@incomm.com vhari@incomm.com skeshari@InComm.com mmurarisetty@incomm.com stadoori@incomm.com"

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

		}

		stage('Build-Release'){
			if (userInput == 'Build'){
				echo "Building a Snapshot Image to Maven Artifactory"
				sh "${maven} -B -f pom.xml -U -X clean compile jib:build"
            }

			else if (userInput == 'Release'){
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def artifactVersion = pom.getVersion();
                def artifactId = pom.getArtifactId();
      			String[] tag = mavenReleaseVersion.split('-');
				image_tag = "${tag[0]}"

				echo "Docker Release for tag: ${image_tag}"
				sh "${maven} clean compile com.google.cloud.tools:jib-maven-plugin:2.0.0:build -Dimage=docker.maven.incomm.com/${artifactId}:${image_tag}"
				sh "${maven} compile jib:build"
				sh "git tag ${image_tag} && git push origin ${image_tag}"
			} else { echo "no build" }
		}

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
		
	catch (Exception e) {
		echo "ERROR: ${e}"
		currentBuild.result  = 'FAILURE'
	} finally {
        sendEmailv3(emailDistribution, getBuildUserv1());
	}
		
}  //end of node


///// The End


