import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


//gitURL="https://github.com/InComm-Software-Development/FSAPI-Security-New-SBC3.git"
gitBranch="${Branch}"
gitCreds="scm-incomm"
prodConfigsRepo="https://github.com/InComm-Software-Development/FSAPI_Prod_ConfigFiles.git"

credsName = "scm_deployment"
userInput="Promote"
targetEnv="${target_env}"

testName="myTest"

targets = [
	'prod_SPFSSRV10FV':  ['10.41.6.113'],
	'prod_SPFSSRV11FV':  ['10.41.6.114'],
	'prod_SPFSSRV12FV':  ['10.41.6.115'],
]

emailDistribution="ppiermarini@incomm.com FS-MiddlewareAtlanta@incomm.com"
//General pipeline

artifactDeploymentLoc ="/opt/incomm-apisecurity/deploy/"
propertiesDeploymentLoc="/opt/incomm-apisecurity/config"
srcProperties="incomm-apisecurity/config/"
serviceName="standalone-apisecurity.service"
pipeline_id="${env.BUILD_TAG}"

artifactVersion="${artifactVersion}"

user="jboss"
group="jboss"
chmod="750"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"


///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm'
artifactId = 'APISecurity'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = 'APISecurity.jar'



node('linux'){
	
//jdk = tool name: 'openjdk-11.0.5.10'
//env.JAVA_HOME = "${jdk}"
//echo "jdk installation path is: ${jdk}"
//sh "${jdk}/bin/java -version"

def ENV = targetEnv.split('_')
echo "p${ENV[0]}p"
myenv = "p${ENV[1]}p"
currentBuild.result = "SUCCESS"

	try { 

		stage('checkout config branch'){
			cleanWs()
			githubCheckout(gitCreds,prodConfigsRepo,gitBranch)
		}
		

		stage('Approval'){

			if ((userInput == 'Promote') && ("${ENV[0]}" == "prod")){
				   
                    echo "Inside approval block"
                    operators= "['ppiermarini', 'dkumar', 'ssanka', 'jkumari', 'mkhan']"
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
                    } //if prod
			}
        }//approval stage
        
        stage('Get Artifact'){
			if ((userInput == 'Promote') && (approval_status == 'Proceed')){

				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				sh "ls -ltra"
			} else {
				echo "Something went wrong"
			}
			
		}

		stage("Deployment to ${targetEnv}"){
			Servers = targets.get(target_env)
			
			if (userInput == 'Promote'){

				deployComponents(targetEnv, targets[targetEnv],"${artifactName}")

			} else {
				echo "user input not Promote"
			}

		}

	stage('Notify'){
		if(userInput == 'Build'){
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "${JOB_NAME}", 
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
        subject: "${JOB_NAME}", 
				body: """
<html>
<p>**************************************************</p>
<ul>
<li>STATUS: ${currentBuild.result}</li>
<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
<li>UserInput = ${userInput}</li>
<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
<li>ServerNames: ${targetEnv}= ${Servers}</li>
</ul>
<p>**************************************************</p>\n\n\n
</html>
"""			
		}
		
	} //notify
		
		
		
	} catch (Exception exc) {
			currentBuild.result="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
		echo "#############"
		echo "#  SUCCESS  #"
		echo "#############"
	}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	}
	parallel stepsInParallel
	
}



def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl stop ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${propertiesDeploymentLoc}/*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}
		scp -q -o StrictHostKeyChecking=no ${srcProperties}*.properties root@${target_hostname}:${propertiesDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLoc}/${Artifact}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/${Artifact}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${propertiesDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${propertiesDeploymentLoc}/'
		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl restart ${serviceName}'
		"""
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
		//if(1 ){
		//	println "ERROR todo "
		//} else {
		//	println "results"
		//}
}

def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End