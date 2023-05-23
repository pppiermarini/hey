import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

//  Modify for your BRANCHING
//  you should have a development branch, a branch to merge and build when going to QA
//  and then a release branch where the release is built typically master or trunk


//gitRepository = "https://github.com/InComm-Software-Development/ctps-ifp-rest.git"
//gitBranch = "${branch}"
//gitCreds = "scm-incomm"

credsName = "scm_deployment"
// inputs from build with parameters
userInput = "Promote"  //hard coded for deployment only
targetEnv = "${target_env}"

testName = "myTest"

targets = [
	'load_servers': ['sllgtp01ifpv','sllgtp02ifpv','sllgtp03ifpv','sllgtp04ifpv','sllgtp05ifpv','sllgtp06ifpv','sllgtp07ifpv','sllgtp08ifpv','sllgtp09ifpv','sllgtp10ifpv'],
]

//sllgtp01ifpv 10.42.5.229
//sllgtp02ifpv 10.42.5.230 
//sllgtp03ifpv 10.42.5.231
//sllgtp04ifpv 10.42.5.232
//sllgtp05ifpv 10.42.5.233
//sllgtp06ifpv 10.42.5.234
//sllgtp07ifpv 10.42.5.235
//sllgtp08ifpv 10.42.5.236
//sllgtp09ifpv 10.42.5.237
//sllgtp10ifpv 10.42.5.238

emailDistribution = "ppiermarini@incomm.com sthulaseedharan@incomm.com"

//General pipeline 

artifactDeploymentLoc = "/app/ifprestapi"
libDeploymentLoc = ""
instanceLogs = "/app/ifprestapi/logs"

Servers = ""
serviceName = "ifprestapi"
pipeline_id = "${env.BUILD_TAG}"

maven = "/opt/apache-maven-3.2.1/bin/mvn"

//Artifact Resolver	input specifics
repoId ='maven-release'
groupId ='com.incomm.ifpapp'
artifactId ='ifp-restapi'
env_propertyName ='ART_VERSION'
artExtension ='jar'
artifactName ='ifp-restapi.jar'

artifactVersion = "${artifact}"

//globals
test_suite = "none"
user = "ifprest"
group = "ifprest"
filePermission = "664"
folderPermission = "755"


userApprove = "Welcome to the new Jenkinsfile"
envInput = "null"
myenv = ""


node('linux'){
	
jdk = tool name: 'openjdk-11.0.5.10'
env.JAVA_HOME = "${jdk}"
echo "jdk installation path is: ${jdk}"
sh "${jdk}/bin/java -version"

def ENV = targetEnv.split('_')
echo "p${ENV[0]}p"
myenv = "p${ENV[1]}p"
currentBuild.result = "SUCCESS"
	try {

		// check out the code no matter what. when promoting a release check out the release tag or branch so the
		// proper configurations can be deployed
//	stage('checkout'){
//		cleanWs()
//		echo "p${gitCreds}p"
//		echo "p${gitRepository}p"
//		echo "p${gitBranch}p"
//		githubCheckout(gitCreds, gitRepository, gitBranch)
//	}

	stage('Build'){

		if (userInput == 'Build') {
			//sonar  pipelineSonar  sonarqube.incomm.com
		//	withSonarQubeEnv('sonarqube.incomm.com'){
			sh "${maven} clean deploy -e -U -DskipTests -Dmaven.javadoc.skip=true"
			sh "cp target/${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"

		//	}

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
			
		} else {
			echo "no build"
		}

	} //stage


//	stage('SonarQube Quality Gate Check') {
		//	if (userInput == 'Build'){
					
		//		sleep(20) // this sleep needs to be here to delay checking for OK immediately or else it fails QG
				//sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${sonarProjectName} -Dsonar.sources=src/"
				
		//		timeout(time: 3, unit: 'MINUTES') {	
		//			def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
		//				if (qg.status != 'OK') {
		//				error "Pipeline aborted due to quality gate failure: ${qg.status}"
		//				}
		//		echo "after QG timeout"
		//		}
		//			
		//	} else {
		//			echo "Quality Gate not needed for Release or Promote"
		//	}
//	}//end QualityGate

		//select the artifact 
	stage('Approval'){
					
		if (userInput == 'Promote'){

		   if ((ENV[0] =='prod'))
			{
				echo "Inside approval block"
				operators= "['awadhwa','dstovall']"
				def Deployment_approval = input message: 'Deploying to PROD', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
				echo "${Deployment_approval}"
				approval_status = "${Deployment_approval['approval_status']}"
				def operator = "${Deployment_approval['approver']}"
				String op = operator.toString()

				if (approval_status == 'Proceed'){
					echo "Operator is ${operator}"
					if (operators.contains(op))
					{
						echo "${operator} is allowed to deploy into ${targetEnv}"
					}
					else
					{
						throw new Exception("Throw to stop pipeline as user not in approval list")
					}
				}else {
				throw new Exception("Throw to stop pipeline as user selected abort")
				}
			}
		} 
	}


	stage('Get Artifact'){
		if (userInput == 'Promote'){

			artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	

		} else {
			echo "build"
		}
		
	}

	stage('Deployment'){
		Servers = targets.get(target_env)
		
		if (userInput == 'Promote') {

			deployComponents(target_env, targets[target_env], "${artifactName}")

		} else {
			
			echo " Build was selected, no deployment, use Promote"
		}

	}


	stage('Notify'){
		if(userInput == 'Build'){
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "ifprestapi-pipeline", 
				body: """
<html>
<p>**************************************************</p>
<ul>
<li>STATUS: ${currentBuild.result}</li>
<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
<li>UserInput = ${userInput}</li>
</ul>
<p>**************************************************</p>\n\n\n
</html>
"""
		} else {
			
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "ifprestapi-pipeline", 
				body: """
<html>
<p>**************************************************</p>
<ul>
<li>STATUS: ${currentBuild.result}</li>
<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
<li>UserInput = ${userInput}</li>
<li>Artifact: ${artifactId}-${artifactVersion}.${artExtension}</li>
<li>ServerNames: ${targetEnv}= ${Servers}</li>
</ul>
<p>**************************************************</p>\n\n\n
</html>
"""			
		}
		
	} //notify

	} catch (exc) {

		stage('Notification'){
			currentBuild.result = 'FAILURE'
			echo 'ERROR:  ' + exc.toString()
			throw exc
		}

	} finally {

		stage('Notification'){
			currentBuild.result = 'SUCCESS'
			//xsendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)	
		}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def deployComponents(envName, targets, Artifact){

	echo "my env= ${envName}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { deploy(it, artifactDeploymentLoc, Artifact) }]
	}
	parallel stepsInParallel

}


def deploy(target_hostname, artifactDeploymentLoc, Artifact) {

	echo " the target is: ${target_hostname}"
	
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl stop ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactName}'
		scp -q -o StrictHostKeyChecking=no ${artifactName} root@${target_hostname}:${artifactDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${filePermission} $artifactDeploymentLoc/${artifactName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl start ${serviceName}'
	"""
	}
}
///// The End
