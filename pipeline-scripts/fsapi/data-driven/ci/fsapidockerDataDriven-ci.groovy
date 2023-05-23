import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


// Jenkins vars
gitRepository = "${GIT_REPOSITORY}"
gitBranch = "${BRANCH}"
pipelineAction = "${PIPELINE_ACTION}"
pipeline_id = "${env.BUILD_TAG}"
maven="/opt/apache-maven-3.6.3/bin/mvn"

// Pipeline Globals
gitCreds = "scm-incomm"
testName = "myTest"
maven="/opt/apache-maven-3.6.3/bin/mvn"
snyk = "/usr/bin/snyk-linux"
snykHtml = "/usr/bin/snyk-to-html-linux"
scanCodeDir = ""
pomPath = "pom.xml"
reports = []
snykTokenId = "snyk-fsapi"  
artifactId = ""                     
artifactVersion = ""              
emailDistribution = "jrivett@incomm.com dstovall@incomm.com dkumar@incomm.com schennamsetty@incomm.com"

imageName = "ipc-openjre8-alpine"

// Data Driven Placeholders
projectProperties = null

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

		// Reading data-driven pipeline values from YML file
    	stage('Read YAML file') {
            echo 'Reading dataDriven.yml file'
            projectProperties = readYaml (file: 'fsapiDockerDataDriven.yml')
            if (projectProperties == null) {
                throw new Exception("dataDriven.yml not found in the project files.")
            }

            // TODO: Ensure the values in this sanity check match the values in YML
            echo "Sanity Check"
            if (projectProperties.gitInfo == null || projectProperties.imageInfo == null) {
				echo "Sanity Check broke"
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        }

		stage('Build-Release'){
echo "build-release stage: ${PIPELINE_ACTION}"
			if (PIPELINE_ACTION == 'Build'){

				echo "Building a Snapshot Image to Maven Artifactory"
				
				//sh "${maven} clean deploy -f pom.xml -DskipTests=true"
				sh "${maven} -B -f pom.xml -U -X clean compile jib:build"
				}
			else if (PIPELINE_ACTION == 'Release'){
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
