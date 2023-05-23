import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

userInput = "${BUILD_TYPE}"
gitRepository="${GIT_REPOSITORY}"
gitBranch="${BRANCH}"
snykTokenId = "${SNYK_TOKEN_ID}"
jdkVersion = "${JDK_VERSION}"
maven = "${MAVEN_LOCATION}"
// /opt/apache-maven-3.2.5/bin/mvn

gitCreds="scm-incomm"
emailDistribution="jrivett@incomm.com dstovall@InComm.com ppiermarini@incomm.com"
registry="docker.maven.incomm.com"
jfrog = "/usr/bin/jfrog"
imageVersion = ""
imageName = ""

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
			pom = readMavenPom file: 'pom.xml'
			imageName = pom.getArtifactId();
			imageVersion = pom.getVersion();

			if (userInput == 'Build') {
				echo "Build"
				sh "${maven} -B -f pom.xml -U clean compile jib:build"
			}
			// this release process should be promoting a snapshot that's already there - not doing another build
			else {
				allTags = callDockerRegistry(imageName)
				selectedTag = singleInputRequested(allTags)
				String[] tag = selectedTag.split('-');
				releaseTag = "${tag[0]}"

				echo "Docker Release for tag: ${releaseTag}"

				withCredentials([usernamePassword(credentialsId: 'scmdocker', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
					sh """ ${jfrog} rt docker-promote ${imageName} docker-local docker-prod --source-tag="${selectedTag}" --target-tag="${releaseTag}-RELEASE" --url="https://docker.maven.incomm.com/artifactory" --user=${USER} --password=${PASS} """
				}

				sh "git tag ${releaseTag} && git push origin ${releaseTag}"
			}
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
	} catch (Exception e) {
		echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"
	} finally {
		sendEmailv3(emailDistribution, getBuildUserv1())
	}
}
