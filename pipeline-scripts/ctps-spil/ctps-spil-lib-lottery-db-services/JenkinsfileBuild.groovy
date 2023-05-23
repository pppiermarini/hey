import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


gitRepository="https://github.com/InComm-Software-Development/ctps-spil-lib-lottery-db-services.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${action}"

folder=""

emailDistribution="HPokala@incomm.com"
//General pipeline


pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

currentBuild.result = 'SUCCESS'

node('linux1'){
	
	try{
	
		jdk=tool name:"${jdkVersion}"
		env.JAVA_HOME="${jdk}"
		echo "jdk installation path is: ${jdk}"
		sh "${jdk}/bin/java -version"
		echo "Build_Type = ${userInput}"

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
		
		
		stage('Build'){

			if (userInput == 'Build'){

				//withSonarQubeEnv('sonarqube.incomm.com'){
					//sh "${maven} clean deploy -f pom.xml -e -U -X -DskipTests sonar:sonar"
					sh "${maven} clean deploy -DskipTests"

				//}
			
			}else if (userInput == 'Release'){
		
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare"
				sleep(3)
				sh "${maven} org.apache.maven.plugins:maven-release-plugin:2.5.3:perform"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"
				
			}else {
			echo "no build"	
			}

		} //stage
		

		
	} catch (any) {
		echo "Muy Mal"
		
	} finally {
		
		if (currentBuild.result == "SUCCESS"){
			
			stage('Notification'){
				currentBuild.result = 'SUCCESS'
				sendEmail(emailDistribution, gitBranch, userInput )	
			}
			
		}else{
			
			stage('Notification'){
				currentBuild.result = 'FAILURE'
				sendEmail(emailDistribution, gitBranch, userInput )
				//echo 'ERROR:  '+ exc.toString()
				//throw exc
			}
		}
	}
	

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////




///// The End
