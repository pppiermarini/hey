import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


svnUrl="https://svn.incomm.com/svn/fsitRepos/Platforms/GC/lib/GreenCard-Service/branches/${Branch}"


emailDistribution="ppiermarini@incomm.com"
//emailDistribution="Greencard-Dev@incomm.com ppiermarini@incomm.com"
//General pipeline

maven="/opt/apache-maven-3.2.1/bin/mvn"
currentBuild.result="SUCCESS"



node('linux'){

jdk=tool name:'openjdk-11.0.7.10-0'
env.JAVA_HOME="${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"

	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			checkoutSVN(svnUrl)
		}
		echo "$userInput"
		
		stage('Build'){

			if((userInput != 'Promote') && (userInput != 'Release') && (userInput != 'QABuild')){
				//withSonarQubeEnv('sonar'){
					sh "${maven} clean deploy -X -U -DskipTests"
				//}
			} else if (userInput == 'Release') {
			
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
			}else {
				echo "no build"	
			}

		} //stage
		

		stage('Quality Gate'){

			if (userInput == 'Build'){
				echo "Quality Gate commented out for now"
			//	sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG

				
			//	timeout(time: 3, unit: 'MINUTES') {	
			//		def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
			//			if (qg.status != 'OK') {
			//			error "Pipeline aborted due to quality gate failure: ${qg.status}"
			//			}
			//	echo "after QG timeout"
			//	}
				
			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}


	} catch(exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		stage("Notification"){
		notifyBuild(emailDistribution)
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
