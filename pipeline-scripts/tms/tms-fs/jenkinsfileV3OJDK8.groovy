import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
//tms-fs (fraudmonitoring)
//#################

@Library('pipeline-shared-library') _

//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk

gitRepository = "https://github.com/InComm-Software-Development/cfes-tmsfs-fraudmonitoring.git"
gitBranch = "${Branch}"
gitCreds = "scm-incomm"

credsName = "scm_deployment"

// inputs from build with parameters
userInput = "${BUILD_TYPE}"
targetEnv = "${target_env}"

testName = "myTest"

targets = [
	'dev_sdtmsapp01v': ['10.42.18.207'],
	'dev_sdtmsapp02v': ['10.42.18.208'],
	'dev_sdtmsapp03v': ['10.42.18.209'],
	'dev_sdtmsapp04v': ['10.42.18.210'],
	'qa_SQS00TMSAPP01V': ['10.42.80.24'],
	'qa_SQS00TMSAPP02V': ['10.42.80.25',],
	'qa_sqltmsapp01v': ['10.42.83.104'],
	'qa_sqltmsapp02v': ['10.42.83.105'],
	'prod_spltmsfsapp01fv': ['10.41.7.110'],
	'prod_spltmsfsapp02fv': ['10.41.7.111'],
	'prod_spltmsfsapp03fv': ['10.41.7.112'],
	'prod_spltmsfsapp04fv': ['10.41.7.113'],
	'prod_spltmsfsapp05fv': ['10.41.7.114'],
	'prod_spltmsfsapp06fv': ['10.41.7.115']
]

emailDistribution="ppiermarini@incomm.com dstovall@incomm.com"
//General pipeline 

approvalData = [
	'operators': "[awadhwa,amohammad,schahanapally,nprasobhan,dstovall]",
	'adUserOrGroup' : 'awadhwa,amohammad,schahanapally,nprasobhan,dstovall',
	'target_env' : "${targetEnv}"
]

artifactDeploymentLoc = "/var/opt/pivotal/pivotal-tc-server-standard/tms-fs-instance/webapps"
libDeploymentLoc="/var/opt/pivotal/pivotal-tc-server-standard/tms-fs-instance/lib"
instanceLogs="/var/opt/pivotal/pivotal-tc-server-standard/tms-fs-instance/logs"
def artifactloc = "${env.WORKSPACE}"

serviceName = "tms-fs-instance"

pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

//Artifact Resolver	input specifics
repoId ='maven-all'
groupId ='com.incomm.services.tmsfs'
artifactId ='tms-fs-service'
env_propertyName ='ART_VERSION'
artExtension ='war'
artifactName ='tms-fs-service.war'

artifactRealName = 'tms-fs-service'

//globals
test_suite = "none"
user = "tcserver"
group = "pivotal"
filePermission = "644"
folderPermission = "755"
star = "*"
prop = "*.properties"
yml = "*.yml"
xml = "*.xml"

userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
envlevel = "null"
svnrevision = "null"
relVersion = "null"
sonarStatus = "null"
serviceStatus = "null"
myenv = ""
JDKVersion="${JDK_Version}"

node('linux') {
	echo "Following build parameters selected: "
	echo "BUILD_TYPE - ${userInput}"
	echo "BRANCH - ${gitBranch}"
	echo "target_env - ${targetEnv}"
	echo "JDK_Version - ${JDKVersion}"

	jdk = tool name: "${JDKVersion}"
	env.JAVA_HOME = "${jdk}"
	echo "jdk installation path is: ${jdk}"
	sh "${jdk}/bin/java -version"

	String[] ENV;
	ENV = targetEnv.split('_');
	//echo "p${ENV[0]}p"
	myenv = "${ENV[0]}"

	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
		stage('checkout') {
			cleanWs()
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		stage('Build') {

			if (userInput == 'Build') {

				pom = readMavenPom file: 'pom.xml'
				relVersion = pom.getVersion();

				//sonar  pipelineSonar  sonarqube.incomm.com
				withSonarQubeEnv('sonarqube.incomm.com') {
					sh "${maven} clean deploy -e -U -DskipTests sonar:sonar"
					sh "cp target/${artifactName} ."
				}

			} else if (userInput == 'Release') {

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
				//sh "/bin/cp -f mavenrelease ${pipelineData}/dev"

			} else if (userInput == 'Test') {

				echo "Running only tests"
				
			} else if (userInput == 'Promote') {
				echo "Running PROMOTE"
			}

		} //stage

		stage('Get Artifact') {

			if (userInput == 'Build' || userInput == 'Promote') {
				 // Select and download artifact
				list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
				echo "the list contents - ${list}"
				relVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
				parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
				sleep(3)
				artifactWget(repoId, groupId, artifactId, artExtension, relVersion)
				echo "the artifact version - ${relVersion}"
				sh "ls -ltr"
			}
    	}

		stage('Deployment') {

			echo "deploying this file:  ${artifactId}-${relVersion}.${artExtension}"

			//Only build and promote will deploy to a Server
			if ((userInput == 'Build') || (userInput == 'Promote')) {

				sh"cp ${artifactId}-${relVersion}.${artExtension} ${artifactName}"
				sh"ls -ltr"
				//prelimEnv(target_env, relVersion)
				deployComponents(target_env, targets[target_env], "${artifactId}-${relVersion}.${artExtension}")
			
				//md5SumCheck(targets[target_env],artifactDeploymentLoc,"${artifactId}-${relVersion}.${artExtension}","${artifactId}-${relVersion}.${artExtension}")

			} else {
				echo "not deploying during a release build"
			}
		}

	} catch (exc) {
		echo "Muy Mal"
		stage('Notification'){
			currentBuild.result = 'FAILURE'
			sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)
			echo 'ERROR:  ' + exc.toString()
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

def prelimEnv(target_env, relVersion){

	// prelim staging if needed

	echo "${target_env}"
	echo "Selected=  ${artifactId}-${relVersion}.${artExtension}"
	echo "DEPLOYING TO ${target_env}"
	echo "relVersion= ${relVersion}"
	writeFile file: "relVersion.txt", text: relVersion

	echo "DEPLOYING TO ${target_env}"

	localArtifact = "${artifactId}-${relVersion}.${artExtension}"
	remoteArtifact = "${artifactId}-${relVersion}.${artExtension}"

	echo "local artifact=  $localArtifact"
	echo "remote artifact=  $remoteArtifact"
}


def deployComponents(envName, targets, Artifact) {

	echo "Deploying to - ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { deploy(it, artifactDeploymentLoc, Artifact) }]
	}
	parallel stepsInParallel

}


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo "the target is: ${target_hostname}"
	
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogs}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}'
		scp -q -o StrictHostKeyChecking=no ${artifactRealName}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${myenv}/${star} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${prop} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${xml} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${yml} root@${target_hostname}:${libDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
	"""
	}
}

def getFromArtifactory() {
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
	def stepsInParallel = targets.collectEntries {
		["$it" : { tests(it, envName, testName) }]
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
