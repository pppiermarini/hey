import jenkins.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/digitaldelivery-dds-microservices-dds-callback-processor.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"


// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


// ppiermarini@incomm.com vhari@incomm.com  DigitalDeliveryDevs@incomm.com
emailDistribution="rkale@incomm.com ppiermarini@incomm.com vhari@incomm.com  DigitalDeliveryDevs@incomm.com"
//General pipeline 

pipeline_id="${env.BUILD_TAG}"
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"
currentBuild.result="SUCCESS"

node('windows'){
	try { 

		cleanWs()

		stage('Setting JDK') {
			jdk=tool name:"${jdkVersion}"
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			bat "${jdk}\\bin\\java -version"
		}
		
		stage('Github Checkout') {
			echo "Checking out Github Repo for Branch: ${gitBranch}"
			githubCheckout(gitCreds,gitRepository,gitBranch)

		}

		stage('Build'){

			if (userInput == 'Build'){
			bat "${maven} clean compile -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests"	
				withSonarQubeEnv('sonarqube.incomm.com'){
					//bat "${maven} clean deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests sonar:sonar"
				bat "${maven} sonar:sonar -Dsonar.branch.name=${BRANCH}"
				}
				bat "${maven} deploy -f ${env.WORKSPACE}/pom.xml -e -U -X -DskipTests"
				
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
		
		
		/*stage('Quality Gate'){
			
			if ((userInput != 'Release')||(userInput != 'Promote')){

			echo "Quality Gate commented out for this build"
		//		sleep(25) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
		//			timeout(time: 1, unit: 'HOURS') {	

		//			def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
		//				if (qg.status != 'OK') {
		//				error "Pipeline aborted due to quality gate failure: ${qg.status}"
		//				}
		//		echo "after QG timeout"
		//		}
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}*/


		
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "Build completed"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node


///// The End
