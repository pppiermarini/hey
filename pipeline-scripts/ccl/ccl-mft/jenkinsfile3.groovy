import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _
//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk


gitRepository = "https://github.com/InComm-Software-Development/ccl-mft"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

// inputs from build with parameters
userInput = "${BUILD_TYPE}"
target_env = "${target_env}"
test_suite = "${test_suite}"
testName = "myTest"

targets = [
	'dev-a': ['sdcclappa01v.unx.incommtech.net'],
    'dev-b': ['sdcclappb01v.unx.incommtech.net'],
    'qa-a': [''],
    'qa-c': [''],
    'qa-d': [''],
	'qa-int': [''],
	'uat-2'  : [''],
    'uat-3'  : [''],
    'uat-4'  : [''],
    'uat-5'  : [''],
    'uat-6'  : [''],
    'uat-ui-2'  : [''],
	'stg-tst': ['']
]

emailDistribution="vhari@incomm.com"
//General pipeline 
artifactDeploymentLoc = "/srv/ccl-apps/ccl-mft"
def artifactloc = "${env.WORKSPACE}"

serviceName = "ccl-mft.service"

pipeline_id = "${env.BUILD_TAG}"
maven = "/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.cclp'
artifactId = 'ccl-mft'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''

//globals
userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"

currentBuild.result = 'SUCCESS'

//userInput = InputAction()

node('linux'){

			jdk=tool name:'openjdk-11.0.5.10'
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
	try { 

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout'){
			cleanWs()
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}
		
		
		stage('Build'){

			if (userInput == 'Build'){
				withSonarQubeEnv('sonarqube.incomm.com'){

                     sh "${maven} clean deploy -f pom.xml -e -U -DskipTests sonar:sonar -Dsonar.branch.name=${BRANCH}"   
					sh "cp target/${artifactId}*.${artExtension} ."

				}
	
			
			}else if (userInput == 'Release'){
				
				echo "Maven Release Build"
				echo "Maven Release:Prepare..."
				pom = readMavenPom file: 'pom.xml'
				def mavenReleaseVersion = pom.getVersion();
				echo " maven release= " + "${mavenReleaseVersion}"
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(3)
				sh "${maven} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true'"
				sleep(4)
				def str = mavenReleaseVersion.split('-');
				def myrelease = str[0];
				writeFile file: "mavenrelease", text: myrelease
				
			}else {
			echo "no build"	
			}

		} 		
		
		stage('Quality Gate'){
			
			if (userInput == 'Build'){

                    echo "after QG timeout"
        } else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}
		stage('Get Artifact'){

			if (userInput == 'Promote') {
				getFromArtifactory()

			} else {
				echo "not getting artifact during this build type"
			}

		}
		
		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Build'){
					target_env = 'dev-a'
					echo "Build always deployes to "
			//prelimEnv(target_env, relVersion)
			deployComponents(target_env, targets[target_env], "${artifactId}.${artExtension}")
			}
			else {
				echo "not deploying during a release build"
			}

		}
		
		stage('Testing'){
			node ('QAAutomation'){

			if ((userInput == 'Build')||(userInput == 'Promote')){

			smokeTesting(target_env, targets[target_env], testName)
			} else {
				echo "not testing during a release build"
			}
			}

		}

		
	} catch (exc) {
		echo "Muy Mal"
		stage('Notification'){
			currentBuild.result = 'FAILURE'
			sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)
			echo 'ERROR:  '+ exc.toString()
			throw exc
		}
		
	} finally {
		echo " Muy Bien "
		stage('Notification'){
			currentBuild.result = 'SUCCESS'
			//xsendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)	
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*def prelimEnv(target_env, relVersion){
	
	// prelim staging if needed
	
	echo "${target_env}"
	echo "Selected=  ${artifactId}-${relVersion}.${artExtension}"
	echo "DEPLOYING TO ${target_env}"
	echo "relVersion= ${relVersion}"
	writeFile file: "relVersion.txt", text: relVersion

	echo "DEPLOYING TO ${target_env}"

	localArtifact="${artifactId}-${relVersion}.${artExtension}"
	remoteArtifact="${artifactId}-${relVersion}.${artExtension}"
	
	echo " local artifact=  $localArtifact"
	echo "remote artifact=  $remoteArtifact"	
}*/


def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"

	sh """
		    ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '$artifactDeploymentLoc/bin/ccl-cache.sh stop > /dev/null'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'mv $artifactDeploymentLoc/app/${artifactId}*.${artExtension} $artifactDeploymentLoc/app/archive/'
			scp -q -i ~/.ssh/pipeline ${artifactId}*.${artExtension} root@${target_hostname}:$artifactDeploymentLoc/app/
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/chown -R jboss:jboss $artifactDeploymentLoc/app/${artifactId}*.${artExtension}'
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/ln -fs $artifactDeploymentLoc/app/${artifactId}*.${artExtension} $artifactDeploymentLoc/app/${artifactId}.${artExtension}'
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/chown -R jboss:jboss $artifactDeploymentLoc/app/${artifactId}'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '$artifactDeploymentLoc/bin/ccl-cache.sh start > /dev/null'
	"""
		
    	
}

def getFromArtifactory(){
	if (userInput == "Promote") {
		echo "Select an artifact from Artifacory"

		relVersion = getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)

		echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"
	} else {
		echo "not promoting-Skipping"
	}
}

def smokeTesting(envName, targets, testName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { tests(it, envName, testName) } ]
	}
	parallel stepsInParallel

}//end smoketesting

def tests(target, envName, testName){
	
	echo " Smoke Testing on ${target}"
	echo "my test = ${testName}"
	sleep(1)
		dir('testresults'){
			//println "Run Test Script"
			//http://localhost:1505/lisa-invoke/runTest?testCasePath=Projects\\AppleIT\\Tests\\AppleDevTest.tst -OutFile testResults.xml -Verbose
			// String results = readFile 'testresults.xml'
		}
}
///// The End
