import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _



gitRepository="https://github.com/InComm-Software-Development/ctps-ordergiftcard-ordermanagement.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"
testName="myTest"

//General pipeline
emailDistribution="sthulaseedharan@incomm.com kkoya@incomm.com pabbavaram@incomm.com rkale@incomm.com vhari@incomm.com ppiermarini@incomm.com stadoori@incomm.com dstovall@incomm.com"


artifactVersion="null"

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

currentBuild.result = 'SUCCESS'

node('linux'){
	try { 
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}

		stage('Setting JDK 11') {
				jdk=tool name:"openjdk-11.0.5.10"
				env.JAVA_HOME="${jdk}"
				echo "jdk installation path is: ${jdk}"
				sh "${jdk}/bin/java -version"
		}
		
		
		stage('Build-Release'){
			pom = readMavenPom file: 'pom.xml'
			 artifactVersion = pom.getVersion();
			
			if (userInput == 'Build'){

				echo "Building a Snapshot Image to Maven Artifactory"
				sh "${maven} clean deploy -DskipTests"
				//sh "${maven} clean deploy -DskipTests=true"
				pom = readMavenPom file: 'pom.xml'
				artifactVersion = pom.getVersion();
				
				}
				
			else if (userInput == 'Release'){
				
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Dmaven.test.skip=true -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Dmaven.test.skip=true -Darguments='-Dmaven.javadoc.skip=true'" 
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"

			} else { echo "no build" }

		} //stage
		

	} catch (exc) {
		
		currentBuild.result = 'FAILURE'
		echo 'ERROR:  '+ exc.toString()
		throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.currentResult  == "SUCCESS"){
		echo "${currentBuild.currentResult}"
		emailext mimeType: 'text/html', attachLog: true, 
			to: "${emailDistribution}",
			subject: "Build job: ${JOB_NAME}", 
					body: 
					"""
					<html>
							<p>**************************************************</p>
					<ul>
						<li>STATUS: ${currentBuild.currentResult}</li>
						<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
						<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
						<li>Artifact Version: ${artifactVersion}</li>
					</ul>
							<p>**************************************************</p>\n\n\n
					</html>
					"""	
			//echo "if success"
			//emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

		}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}
	

} //end of node
