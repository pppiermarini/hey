import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/OpenCardEntryService.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"



emailDistribution="ppiermarini@incomm.com vhari@incomm.com rkale@incomm.com khande@incomm.com"
//General pipeline 

pipeline_id="${env.BUILD_TAG}"
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"
currentBuild.result="SUCCESS"

node('windows'){
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}

		stage('Setting JDK 11') {
				jdk=tool name:"openjdk-11.0.5.10"
				env.JAVA_HOME="${jdk}"
				echo "jdk installation path is: ${jdk}"
				bat "${jdk}\\bin\\java -version"

		}
		
		
		stage('Build'){

			if (userInput == 'Build'){

				/*withSonarQubeEnv('sonar'){
					//bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"
					bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"
				}*/
				bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests"
				
			}else if (userInput == 'Release'){
				
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				bat "${maven} -X -Darguments=-DskipTests org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				bat "${maven} -X -Darguments=-DskipTests org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//bat "copy /Y mavenrelease $pipelineData\\DEV"
			}else {
			echo "no build"	
			}

		} //stage

		
	} catch(exc) {
			currentBuild.result="FAILED"
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
