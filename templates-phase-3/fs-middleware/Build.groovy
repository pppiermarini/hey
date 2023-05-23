import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// FS-MiddlewareCanada: merchantAPI
//#################

@Library('pipeline-shared-library') _



// inputs from build with parameters
userInput="${userInput}"
gitBranch="${Branch}"


//General pipeline
gitRepository="https://github.com/InComm-Software-Development/GPR-merchantAPI.git"
gitCreds="scm-incomm"

testName="myTest"
folder="C"
//emailDistribution="mli@incomm.com"
emailDistribution="ppiermarini@incomm.com"

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"

echo "Pipeline Parameters: userInput=[${userInput}], Branch=[${Branch}]"


node('linux'){
	
jdk=tool name:'openjdk-11.0.7.10-0'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
	
	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		stage('Build'){

			if (userInput == 'Build'){
				//sonar  pipelineSonar  sonarqube.incomm.com
		        if (runSonar == 'true') {                                
					withSonarQubeEnv('sonarqube.incomm.com'){
					    //sh "${maven} clean deploy -f pom.xml -e sonar:sonar"
						sh "${maven} clean compile -f pom.xml -e"
    					}
                } else {
                    		sh "${maven} clean deploy -f pom.xml -e -U -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
                	}	


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
		

		stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build') {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == 'true') {
                   qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }

		currentBuild.result = 'SUCCESS'
		
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
