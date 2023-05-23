import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

gitRepository="https://github.com/InComm-Software-Development/vms-spil-svc.git"
gitBranch="${BRANCH}"
gitCreds="scm-incomm"

// inputs from build with parameters
userInput="${BUILD_TYPE}"
testName="myTest"


emailDistribution="jrivett@incomm.com FSCorePlatforms-Dev@incomm.com"
//General pipeline

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"
currentBuild.result="SUCCESS"
//runQualityGate = "${RUN_QUALITY_GATE}"
//runSonar = "${RUN_SONAR}"
//JAVA_VERSION="${JAVA}"


node('linux'){

jdk=tool name:"openjdk-11.0.7.10-0"
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"

	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			//listGithubBranches(gitCreds,gitRepository)
			//echo "Checking out p${env.BRANCH_SCOPE}p"
			//cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}
		
		
		stage('Build'){
        		if (userInput == 'Build'){
				/*
				//sonar  pipelineSonar  sonarqube.incomm.com
		                if (runSonar == 'true') {                                
					withSonarQubeEnv('sonarqube.incomm.com'){
					    sh "${maven} clean deploy -f pom.xml -e sonar:sonar"
    					    } 
                		} else {
                    			sh "${maven} clean deploy -f pom.xml -e -U -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
                			}	
				*/
				sh "${maven} clean deploy -f pom.xml -e -U -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
				

			}else if (userInput == 'Release'){

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
				
			}else {
			echo "no build"	
			}

		} //stage
		

		/*stage('Quality Gate') {
            echo "Evaluating the parameters for stage run: userInput=${userInput}, should be Build or Release"
            when (userInput == 'Build' ||  userInput == 'Release' ) {
                echo "Checking quality gate parameter: ${runQualityGate}"
                if (runQualityGate == 'true') {
                   qualityGateV2()
                } else {
                    echo 'Quality Gate option not selected for this run.'
                }
            } 
        }
     */
	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		sendEmailv3(emailDistribution, getBuildUserv1())
		//sendEmail(emailDistribution, userInput, gitBranch) 
		}
	}
	

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End
