import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// CTPS-SPIL
//#################

@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-services-gettokenpja.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${userInput}"


emailDistribution="ppiermarini@incomm.com vpilli@incomm.com"
//emailDistribution="vpilli@incomm.com mpalve@incomm.com psubramanian@incomm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
//Same Maven version from Maven Release Project
//maven="/opt/apache-maven-3.3.9/bin/mvn"
//maven="/opt/apache-maven-3.5.0/bin/mvn"
//maven="/opt/apache-maven-3.6.3/bin/mvn"
maven="/opt/apache-maven-3.2.1/bin/mvn"



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

		}

		stage('Build-Release'){
			
			if (userInput == 'Build') {
				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com'){
					sh "${maven} clean deploy -f pom.xml"
					
				}
				//sh "cp target/${artifactId}*.${artExtension} ${artifactName}"
				
			} else if (userInput == 'Release') {

				echo "Maven Release Build"
				echo "Maven Release:Prepare..."

				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
				
			} else {
				echo "no build"
			}

		}//stage

	} //try 
		
	catch (exc) {
		echo "Compile Error"
		currentBuild.currentResult  == "FAILURE"
		echo 'ERROR:  ' + exc.toString()
		throw exc
		
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
		
} 