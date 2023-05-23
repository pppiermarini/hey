import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository = "https://github.com/InComm-Software-Development/ccl-spil.git"
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
	'stg': ['sscclapp01fv.fastcard.local', 'sscclapp02fv.fastcard.local'],
	'PROD-POOL1': ['spcclapp01fv.fastcard.local'],
	'PROD-POOL2': ['spcclapp02fv.fastcard.local', 'spcclapp03fv.fastcard.local', 'spcclapp04fv.fastcard.local'],
	'PROD-POOL3': ['spcclapp05fv.fastcard.local', 'spcclapp06fv.fastcard.local', 'spcclapp07fv.fastcard.local']
]

emailDistribution="ppiermarini@incomm.com dstovall@incomm.com vhari@incomm.com pchourasia@incomm.com psharma@incomm.com sdhumal@InComm.com rgopal@incomm.com abajaj@incomm.com mjoshi@InComm.com kloganathan@incomm.com"
//General pipeline 
pipelineData = "/app/pipeline-data/cclp-spil"
artifactDeploymentLoc = "/srv/jboss-eap-7.0/standalone/deployments"
def artifactloc = "${env.WORKSPACE}"
Artifact_Type="${artfact_type}"

repoId = 'maven-release'
groupId = 'com.incomm.cclp'
artifactId = 'ccl-spil'

if (Artifact_Type == 'war'){
artifactDeploymentLoc="/srv/jboss-eap-7.0/standalone/deployments"
serviceName="jboss-eap"
env_propertyName = 'ART_VERSION'
artExtension = 'war'
tmpLocation = "/srv/jboss-eap-7.0/standalone/tmp"
dataLocation = "/srv/jboss-eap-7.0/standalone/data"
artifactName = ''
}
else if (Artifact_Type == 'jar'){
artifactDeploymentLoc="/srv/ccl-apps/ccl-spil"
serviceName="ccl-spil.service"
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''
tmpLocation = ''
dataLocation = ''
}

relVersion = "null"
currentBuild.result = 'SUCCESS'

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
			githubCheckout(gitCreds, gitRepository, gitBranch)
		}

		stage('Build'){

            cclbuildrelease(userInput, gitBranch, artifactId, artExtension)
                
		} //stage


		stage('Quality Gate'){

			if ((userInput == 'Build')&&(params.Run_QualityGate == true)) {
				qualityGate()

			} else {
				echo "Quality Gate not needed for Release or Promote"
			}
		}

		stage('Approval'){
            	
			if (userInput == 'Promote'){

               if ((target_env=='dev-a')||(target_env=='PROD-POOL1')||(target_env=='PROD-POOL2')||(target_env=='PROD-POOL3'))
                {
                    echo "Deploying to ${target_env} requires approval"
                    operators= "['psharma','vhari','mjoshi']"
                    def Deployment_approval = input message: 'Deploying to FCV', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
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

		//select the artifact 
		stage('Get Artifact'){

			if (userInput == 'Promote') {
				getFromArtifactory()

			} else {
				echo "not getting artifact during this build type"
			}

		}

		stage('Deployment'){
			echo "This is where we do a bunch of stuff"

			if (((userInput == 'Build')&&(params.Perform_Deployment == true)) || (userInput == 'Promote')){
				if ((userInput == 'Build')&&(Artifact_Type == 'jar')){
                    //target_env = 'qa-c'
                }else if (userInput == 'Build') {
					target_env = 'dev-a'
					echo "Build always deployes to ${target_env}"
				}
				//prelimEnv(target_env, relVersion)
				echo "deploying to ${target_env}"
				deployComponents(target_env, targets[target_env], "${artifactId}-${relVersion}.${artExtension}")
			} else {
				echo "not deploying during a release build"
			}

		}

		stage('Testing'){
			node('QAAutomation'){
				cleanWs()
				if ((userInput == 'Build') || (userInput == 'Promote') ||  (userInput == 'Test')) {
					gitRepository = "https://github.com/InComm-Software-Development/ccl-qa-automation-spil"
					gitBranch = ""
					gitCreds = "scm-incomm"
                    testdir = "ccl-qa-automation-spil"
					dir("${testdir}"){
						githubCheckout(gitCreds, gitRepository, gitBranch)
					}

                    ccltestrun(test_suite, testdir, ReadyAPI_License, ReadyAPI_TestRunner, target_env, emailDistribution)

				} else {
					echo "not testing during a release build"
				}
			}

		}

		echo "inside try block"

	} catch (Exception e) {
		//stage('Notification'){
			echo "Something went wrong"
			currentBuild.result = 'FAILURE'
			//sendEmail(emailDistribution, gitBranch, userInput, target_env, userApprove)
			//echo 'ERROR:  ' + exc.toString()
			//throw exc
		//}

	} finally {
		if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

		//sendEmail(emailDistribution, envlevel, userInput, envInput, "ERROR")

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

		//sendEmail(emailDistribution, envlevel, userInput, envInput, "SUCCESS")

	}
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def deployComponents(envName, targets, Artifact){

	echo "my env= ${envName}"
	echo "Artifact Type: ${Artifact_Type}"
	def stepsInParallel = targets.collectEntries {
		["$it" : { ccldeploy(Artifact_Type, it, artifactDeploymentLoc, Artifact, serviceName, tmpLocation, dataLocation, artifactId, artExtension) }]
	}
	parallel stepsInParallel

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
///// The End
