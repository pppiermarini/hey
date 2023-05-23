import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-eml.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"
//folder="ear"
folderBuildName = null
scanCodeDir = ""
snykTokenId = "snyk-ctps"
snyk = "/usr/bin/snyk-linux"
snykHtml = "/usr/bin/snyk-to-html-linux"
artifactId = "HeartlandPaymentSystems"
reports = []
emailDistribution="ppiermarini@incomm.com dstovall@incomm.com kkarthik@incomm.com"
//emailDistribution="vpilli@incomm.com vhari@incomm.com mpalve@incomm.com ppiermarini@incomm.com psubramanian@incomm.com"


//General pipeline

pipeline_id="${env.BUILD_TAG}"
//Same Maven version from Maven Release Project
//maven="/opt/apache-maven-3.3.9/bin/mvn"
//maven="/opt/apache-maven-3.5.0/bin/mvn"
maven="/opt/apache-maven-3.6.3/bin/mvn"

imageName = "?"
currentBuild.result = 'SUCCESS'

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
		
		stage('Read YAML file') {
            echo 'Reading dataDrivenDocker.yml file'
            projectProperties = readYaml (file: 'dataDrivenDocker.yml')
            if (projectProperties == null) {
                throw new Exception("dataDrivenDocker.yml not found in the project files.")
            }
            if (projectProperties.email.emailDistribution != null) {
                emailDistribution = projectProperties.email.emailDistribution
            }

            echo "Sanity Check"
            if (projectProperties.imageInfo.imageName == null) {
                throw new Exception("Please add the image name")
            }
        }

		stage('Build-Release'){

			if (userInput == 'Build'){
				sh "ls -ltr"
				echo "Building a Snapshot Image to Maven Artifactory"
				withSonarQubeEnv('sonarqube.incomm.com'){
				//sh "${maven} clean deploy -f pom.xml -DskipTests=true"
				sh "${maven} -B -f pom.xml -U -X clean deploy jib:build sonar:sonar"
				}
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

	} //try 
		
	catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.result = 'SUCCESS'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailv3(emailDistribution, getBuildUserv1())	
	}
	
	
}  //end of node

def findPomVersion(folderBuildName) {
	if (folderBuildName == null) {
		getPomVersion()
    }

    else {
    dir("${folderBuildName}") {
    	getPomVersion()
    }
  }
}


def getPomVersion(){
	pom = readMavenPom file: 'pom.xml'
	labelScan = pom.getVersion();

	echo "Pom Version: ${labelScan}"
}
///// The End
