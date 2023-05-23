import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository="https://github.com/InComm-Software-Development/ccl-ui.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"

userInput="${BUILD_TYPE}"
target_env="${target_env}"
testName="myTest"

targets = [
	'dev-a': ['sdcclappa01v.unx.incommtech.net'],
    'dev-b': ['sdcclappb01v.unx.incommtech.net'],
    'qa-a': ['sqcclappa01v.unx.incommtech.net'],
    'qa-c': ['sqlcclappc01v.unx.incommtech.net'],
    'qa-d': ['sqlcclappd01v.unx.incommtech.net'],
	'qa-int': ['sqicclapp01v.unx.incommtech.net'],
	'uat-2'  : ['succlapp02v.unx.incommtech.net'],
    'uat-3'  : ['succlapp03v.unx.incommtech.net'],
    'uat-4'  : ['succlapp04v.unx.incommtech.net'],
    'uat-5'  : ['sulcclapp05v.unx.incommtech.net'],
    'uat-6'  : ['sulcclapp06v.unx.incommtech.net'],
    'uat-ui-2'  : ['sulcclui02v.unx.incommtech.net'],
	'stg': ['sscclui01v.unx.incommtech.net'],
	'PROD-POOL1': ['spcclui01v.unx.incommtech.net'],
	'PROD-POOL2': ['spcclui02v.unx.incommtech.net', 'spcclui03v.unx.incommtech.net']
]

emailDistribution="ppiermarini@incomm.com dstovall@incomm.com vhari@incomm.com pchourasia@incomm.com psharma@incomm.com sdhumal@InComm.com rgopal@incomm.com abajaj@incomm.com mjoshi@InComm.com kloganathan@incomm.com"

def artifactloc="${env.WORKSPACE}"

pipeline_id="${env.BUILD_TAG}"
maven="/opt/apache-maven-3.2.1/bin/mvn"
Artifact_Type="${artfact_type}"
//Artifact Resolver	input specifics

artifactDeploymentLoc="null"
serviceName="null"
repoId = 'null'
groupId = 'null'
artifactId = 'null'
env_propertyName = 'null'
artExtension = 'null'
artifactName = 'null'


if (Artifact_Type == 'war'){
artifactDeploymentLoc="/srv/jboss-eap-7.0/standalone/deployments"
serviceName="jboss-eap"
repoId = 'maven-release'
groupId = 'com.incomm'
artifactId = 'ccl-vms'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'ccl-vms.war'
tmpLocation = "/srv/jboss-eap-7.0/standalone/tmp"
dataLocation = "/srv/jboss-eap-7.0/standalone/data"
}
else if (Artifact_Type == 'jar'){
    artifactDeploymentLoc="/srv/ccl-apps/ccl-ui"
    serviceName="ccl-ui.service"
repoId = 'maven-release'
groupId = 'com.incomm.cclp'
artifactId = 'ccl-ui'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = ''
}

//globals
userApprove="Welcome to the new Jenkinsfile"
envInput="null"
envlevel="null"
svnrevision="null"
relVersion="null"
sonarStatus="null"
serviceStatus="null"

node('linux'){

		if (Artifact_Type == 'jar'){
			jdk=tool name:'openjdk-11.0.5.10'
			env.JAVA_HOME="${jdk}"
			echo "jdk installation path is: ${jdk}"
			sh "${jdk}/bin/java -version"
		}
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
		
				stage('Approval'){
            	
			if (userInput == 'Promote'){

               if ((target_env=='stg')||(target_env=='PROD-POOL1')||(target_env=='PROD-POOL2'))
                {
                    echo "Deploying to ${target_env} requires approval"
                    operators= "['psharma', 'vhari', 'mjoshi']"
                    def Deployment_approval = input message: 'Deploying to PROD', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
                    echo "${Deployment_approval}"
                    approval_status = "${Deployment_approval['approval_status']}"
                    def operator = "${Deployment_approval['approver']}"
					String op = operator.toString()

                    if (approval_status == 'Proceed'){
                        echo "Operator is ${operator}"
                        if (operators.contains(op))
      		            {
                            echo "${operator} is allowed to deploy into ${target_env}"
		                }
		                else
		                {
		                    throw new Exception("Throw to stop pipeline as user not in approval list")
		                }
                    }else {
                    throw new Exception("Throw to stop pipeline as user selected abort")
                    }
                }else{
					echo "Deploying to ${target_env} doesn't required any approvals"
				}
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

			if ((userInput == 'Build')||(userInput == 'Promote')){
				if ((userInput == 'Build')&&(Artifact_Type == 'jar')){
                    target_env = 'qa-c'
                }
                else if (userInput == 'Build'){
					//target_env = 'dev-a'
					echo "Build always deployes to "
				}
			//prelimEnv(target_env, relVersion)
			deployComponents(target_env, targets[target_env], "${artifactId}.${artExtension}")

			} else {
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

    if (Artifact_Type == 'war'){
	sh """
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl stop "${serviceName}.service" > /dev/null'
            		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${tmpLocation}'
		   	ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${dataLocation}'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv $artifactDeploymentLoc/${artifactId}*.${artExtension}'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rfv $artifactDeploymentLoc/${artifactId}*'
			scp -q -i ~/.ssh/pipeline ${artifactId}*.${artExtension} root@${target_hostname}:$artifactDeploymentLoc/
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/chown -R jboss:jboss $artifactDeploymentLoc/${artifactId}*.${artExtension}'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl start "${serviceName}.service" > /dev/null'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find $artifactDeploymentLoc -type f -name ${artifactId}*.failed | wc -l' > commandresult
	
	"""
	def r = readFile('commandresult').trim()
			echo "arr= p${r}p"
			if(r == "1"){
			echo "failed deployment"
			currentBuild.result = 'FAILED'
			} else {
			echo "checking for deployed"
			}
			try {		
			timeout(1) {
				waitUntil {
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find $artifactDeploymentLoc -type f -name ${artifactId}*.deployed | wc -l' > commandresult"""
						def a = readFile('commandresult').trim()
						echo "arr= p${a}p"
						if (a == "1"){
						return true;
						}else{
						return false;
						}
				   }
				   
				}
		}
		catch(exception) {
			echo "${artifactId}-*.${artExtension} did NOT deploy properly. Please investigate"
			abortMessage="${artifactId}-*.${artExtension} did NOT deploy properly. Please investigate"
			currentBuild.result = 'FAILED'
		} }
		else if (Artifact_Type == 'jar'){
    
	sh """
		    ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl stop ${serviceName}> /dev/null'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'mv $artifactDeploymentLoc/app/${artifactId}*.${artExtension} $artifactDeploymentLoc/app/archive/'
			scp -q -i ~/.ssh/pipeline ${artifactId}*.${artExtension} root@${target_hostname}:$artifactDeploymentLoc/app/
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/chown -R jboss:jboss $artifactDeploymentLoc/app/${artifactId}*.${artExtension}'
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/ln -fs $artifactDeploymentLoc/app/${artifactId}*.${artExtension} $artifactDeploymentLoc/app/${artifactId}'
			ssh -q -i /app/jenkins/.ssh/pipeline root@${target_hostname} '/bin/chown -R jboss:jboss $artifactDeploymentLoc/app/${artifactId}'
			ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/systemctl start ${serviceName}> /dev/null'
	"""}
		
    	
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
