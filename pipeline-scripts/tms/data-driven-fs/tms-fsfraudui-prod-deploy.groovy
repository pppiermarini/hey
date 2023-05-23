import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

userInput="Promote"
//targetEnv="${targetEnv}"
targetEnv="${target_env}"

testName="myTest"

targets = [
	'prod': ['10.41.7.110','10.41.7.111','10.41.7.112','10.41.7.113','10.41.7.114','10.41.7.115'],
]
emailDistribution="dstovall@incomm.com ppiermarini@incom.com"
//General pipeline 

artifactDeploymentLoc = "/var/opt/pivotal/pivotal-tc-server-standard/tmsfs-fraud-ui-instance/webapps"
libDeploymentLoc="/var/opt/pivotal/pivotal-tc-server-standard/tmsfs-fraud-ui-instance/lib"
serviceName="tmsfs-fraud-ui-instance"
pipeline_id="${env.BUILD_TAG}"
def artifactloc = "${env.WORKSPACE}"
//tmpLocation="${artifactDeploymentLoc}/tmp"
//dataLocation="${artifactDeploymentLoc}/data"
//pidLocation="/var/run/jboss"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId ='maven-release'
groupId ='com.incomm.ui.tmsfs.fraud'
artifactId ='tmsfs-webapp-fraud-harness'
env_propertyName ='ART_VERSION'
artExtension ='war'
artifactName ='tmsfs-fraud-ui.war'

artifactRealName = 'tmsfs-fraud-ui'

def approver = ''
def approval_status = '' 
def operators = []

//globals
relVersion="null"



node('linux'){
	try { 
		cleanWs()	

		//select the artifact 
		stage('Approval'){
            			
			if (userInput == 'Promote'){

               if ((targetEnv=='prod'))
                {
                    echo "Inside approval block"
                    operators= "['awadha', 'dstovall']"
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
				//getFromArtifactory()
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	

			} else {
				echo "not getting artifact during a release build"
			}
			
		}

		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){
				deployComponents(targetEnv, targets[targetEnv],"${artifactName}")
			} else {
				echo "not deploying during a release build"
			}

		}

		/*stage('Testing'){

			if ((userInput == 'Build')||(userInput == 'Promote')){
			smokeTesting(targetEnv, targets[targetEnv], testName)
			} else {
				echo "not testing during a release build"
			}

		}*/

		
	} catch (Exception exc) {
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
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} stop'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf $artifactDeploymentLoc/${artifactRealName}'
		scp -q -o StrictHostKeyChecking=no ${artifactRealName}.${artExtension} root@${target_hostname}:${artifactDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${target_env}/${star} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${prop} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${xml} root@${target_hostname}:${libDeploymentLoc}
		scp -r -q -o StrictHostKeyChecking=no configurations/properties/${yml} root@${target_hostname}:${libDeploymentLoc}
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} $artifactDeploymentLoc/${artifactRealName}.${artExtension}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} status'
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
						sh """ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find ${artifactDeploymentLoc} -type f -name ${artifactName}*.deployed | wc -l' > commandresult"""
						def a = readFile('commandresult').trim()
						echo "arr= p${a}p"
						if (a == "1"){
						return true;
						}else{
						return false;
						}
				   }
				   
				}
		} catch(exception){
			echo "${artifactName} did NOT deploy properly. Please investigate"
			abortMessage="${artifactName} did NOT deploy properly. Please investigate"
			currentBuild.result = 'FAILED'
		}
    }else{
        echo "Deployed to Contingency Server"
    }
	}

def getFromArtifactory(){
	// prompts user during stage
	getMyArtifact(repoId, groupId, artifactId, artExtension, artifactName)
	echo "Selected Artifact=  ${artifactId}-${relVersion}.${artExtension}"

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