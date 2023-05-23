import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _


userInput="${Build_Type}"
targetEnv="${target_env}"

testName="myTest"

targets = [
    'dev':  ['10.42.16.191'],
    'qaa':   ['10.42.82.110'],
	'qab':   ['10.42.81.17'],
	'uat':   ['10.42.49.215'],
    	'stg':  ['10.41.5.96','10.41.5.97','10.41.5.98','10.41.5.99'],
	//'backup-prod':  ['10.41.5.240','splvmsaps91fv','splvmsaps92fv','splvmsaps93fv','splvmsaps94fv','splvmsaps95fv','splvmsaps96fv','splvmsaps97fv','splvmsaps98fv','splvmsaps99fv','splvmsaps100fv'],
	'backup-prod':  ['splvmsaps77fv'],
	'DenverPOOL':  ['10.191.5.11','10.191.5.12','10.191.5.17','10.191.5.18','10.191.5.19','10.191.5.20','10.191.5.21','10.191.5.227','10.191.5.228'],
	//'PROD-POOL1': ['10.41.5.11','10.41.5.17','10.41.5.21','10.41.5.227'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.22','10.41.5.23','10.41.5.228'],
	//'PROD-POOL3': ['10.41.5.16','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.5.231','10.41.5.232','10.41.5.233','10.41.5.234','10.41.5.235','10.41.7.137','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.141','10.41.7.142','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.146','10.41.7.163','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.167','10.41.7.168','10.41.7.169','10.41.7.170','10.41.7.171','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181'],
	'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167','10.41.5.17','10.41.5.21','10.41.5.227'],
	'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168','10.41.5.22','10.41.5.228'],
	'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.229','10.41.5.230','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.193','10.41.7.194','10.41.7.195','10.41.7.196'],
	//'PROD-POOL3': ['10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.193','10.41.7.194','10.41.7.195','10.41.7.196'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.194','10.41.7.195','10.41.7.196'],
	//'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171'],
	'SPIL-ACTIVATION-POOL1':  ['10.41.7.188','10.41.7.189','10.41.7.190'],
	'SPIL-ACTIVATION-POOL2':  ['10.41.7.191','10.41.7.192','10.41.7.197','10.41.7.198','10.41.7.199','10.41.7.200','10.41.7.201','10.41.7.202','10.41.7.203','10.41.7.204','10.41.7.205'],
	//'SPIL-ACTIVATION-POOL2':  ['10.41.7.202','10.41.7.203','10.41.7.204','10.41.7.205'],
	'Stop-Start-PROD-Redemption-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167','10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168'],
	'Stop-Start-PROD-Redemption-POOL2': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171'],
	//'Stop-Start-PROD-SPIL-ACTIVATION-POOL1':  ['10.41.7.198','10.41.7.199','10.41.7.200','10.41.7.201'],
	'Stop-Start-PROD-SPIL-ACTIVATION-POOL1':  ['10.41.7.188','10.41.7.189','10.41.7.190','10.41.7.191','10.41.7.192','10.41.7.197','10.41.7.198','10.41.7.199'],
	'Stop-Start-PROD-SPIL-ACTIVATION-POOL2':  ['10.41.7.200','10.41.7.201','10.41.7.202','10.41.7.203','10.41.7.204','10.41.7.205'],
	//'Stop-Start-PROD-CHWIVRMMPOS-POOL1':  ['10.41.5.21','10.41.5.22','10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176'],
	//'Stop-Start-PROD-CHWIVRMMPOS-POOL2':  ['10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187'],
	'Stop-Start-PROD-CHWIVRMMPOS-POOL1':  ['10.41.5.21','10.41.5.22'],
	'Stop-Start-PROD-CHWIVRMMPOS-POOL2':  ['10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187'],
	'Stop-Start-PROD-B2B-POOL1': ['10.41.5.227','10.41.5.228','10.41.5.229','10.41.5.230'],
	'Stop-Start-PROD-B2B-POOL2': ['10.41.7.194','10.41.7.195','10.41.7.196'],

]


//emailDistribution="vhari@incomm.com FSCorePlatforms-Dev@incomm.com"
emailDistribution="ppiermarini@incomm.com"
//General pipeline 

currentBuild.result = 'SUCCESS'

artifactDeploymentLoc ="/srv/jboss-eap-7.3/standalone_host/deployments"
serviceName="jboss-as-standalone_host"
pipeline_id="${env.BUILD_TAG}"
tmpLocation="/srv/jboss-eap-7.3/standalone_host/tmp"
dataLocation="/srv/jboss-eap-7.3/standalone_host/data"
pidLocation="/var/run/jboss"
testSuite = "name"
publisttestreport="null"
//gitBranch = "${QA_BRANCH}"


//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-all'
groupId = 'com.incomm.vms.host'
artifactId = 'host'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'inComm_VMS_JBoss.war'
ArtifactVersion = ""
list = ""

def approver = ''
def approval_status = '' 
def operators = []
approvalData = [
	'operators': "[ppiermarini,ppattabiraman,vhari,rgadipalli,nswamy,sminuku,vstanam]",
	'adUserOrGroup' : 'ppiermarini,ppattabiraman,vhari,rgadipalli,nswamy,sminuku,vstanam',
	'target_env' : "${targetEnv}"
]
//globals
relVersion="null"



node('linux'){
	try { 
		cleanWs()	

echo "Build_Type: ${Build_Type}"
echo "target_env:  ${target_env}"
echo "ArtifactVersion: ${ArtifactVersion}"
echo "TEST_SUITE:  ${TEST_SUITE}"
echo "Upload_Test_Report: ${Upload_Test_Report}"


		//select the artifact 
		stage('Approval'){
            			
			//if (userInput == 'Promote'){

               if ((targetEnv=='backup-prod')||(targetEnv=='DenverPool')||(targetEnv=='PROD-POOL1')||(targetEnv=='PROD-POOL2')||(targetEnv=='PROD-POOL3'))
				{
					getApproval(approvalData)        	
       			}
		}
        
        stage('Get Artifact'){
			if (userInput == 'Promote'){
				//getFromArtifactory()
				//artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
		list = artifactResolverV2(repoId, groupId, artifactId, artExtension)

		//echo "the list contents ${list}"

		artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
		parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
		sleep(3)
		artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
		echo "the artifact version ${artifactVersion}"
		sh "ls -ltr"
		sh "mv ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"

			} else {
				echo "not getting artifact during this buildtype"
			}
			
		}

		stage("ServiceOps"){
			echo "Performing Service operation"

			if (userInput == 'STOP_Service'){
				serviceStop(targetEnv, targets[targetEnv])
			} else if (userInput == 'START_Service'){
				serviceStart(targetEnv, targets[targetEnv])
			} else {
				echo "Service reboot handled during deployment"
			}
		}

		
		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){
				deployComponents(targetEnv, targets[targetEnv], "${artifactName}")
			} else {
				echo "not deploying during a release build"
			}

		}

		stage('QA Automation') {
            		echo "Evaluating the parameters for stage run: userInput=${userInput} should not be Release && testSuite=${testSuite} should not be none && projectProperties has test parameters"
            		when ((userInput == 'Promote' && runAtuomatedTests == 'true') || (userInput == 'Test')) {
                	node('QAAutomation') {
				gitRepository = "https://github.com/InComm-Software-Development/FSA_WEB_COMMON.git"
				gridurl = 'http://10.42.84.59:4444/wd/hub'
				def gitCreds = 'scm-incomm'
                    		cleanWs()
				listGithubBranches(gitCreds,gitRepository)
				echo "Checking out p${env.BRANCH_SCOPE}p"
				cleanWs()
				githubCheckout(gitCreds, gitRepository, "${env.BRANCH_SCOPE}")
                    		//vmstestrun(testSuite, workspaceFolder, targetEnvironment, emailDistribution)
                        	echo "test suit selected ${testSuite}"
                        	echo "Running tests on  ${targetEnv}"
                        	echo "Grid URL ${gridurl}"
                        	echo "PublishReport set to ${publisttestreport}"
				jdk=tool name:"openjdk-11.0.7.10-0"
				env.JAVA_HOME="${jdk}"
				echo "jdk installation path is: ${jdk}"
				sh "${jdk}/bin/java -version"
                        	sh "${maven} clean install -Dsuite=$testSuite -DrunMode=grid -Denv=$targetEnv -DgridURL=$gridurl -Dheadless=true -Dconfluence.publishEnabled=$publisttestreport"
                        	publishHTML(target: [
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: true,
							reportDir: "reports/html_report/",
							reportFiles: '*.html',
							reportName: "HTML Report",
							reportTitles: 'HTML Report'
						])
                	}
           		}
       		 }

		
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

def serviceStop(envName, targets){
	
	echo "Stop service on ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { stop(it, serviceName, dataLocation, tmpLocation) } ]
	}
	parallel stepsInParallel
	
}

def stop(target_hostname, serviceName, dataLocation, tmpLocation) {
	
	echo "Stopping service on : ${target_hostname}"
	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c '/home/InComm/CMS/jboss_shutdown_host_7.3.sh' > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /logs*'
		sleep 20
		"""
}

def serviceStart(envName, targets){
	
	echo "Start service on ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { start(it, serviceName, dataLocation, tmpLocation) } ]
	}
	parallel stepsInParallel
	
}

def start(target_hostname, serviceName, dataLocation, tmpLocation) {
	
	echo "Starting service on : ${target_hostname}"
	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c '/home/InComm/CMS/jboss_startup_host_7.3.sh' > /dev/null'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /logs*'
		sleep 20
		"""
}



def deploy(target_hostname, artifactDeploymentLoc, Artifact) {
	
	echo " the target is: ${target_hostname}"
	echo " the deploy location: ${artifactDeploymentLoc}"
	echo " the artifact is: ${Artifact}"
	
	
	sh """
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 /srv/jboss-eap-7.3/bin/jboss-cli.sh > /dev/null || true'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss '/home/InComm/CMS/jboss_shutdown_host_7.3.sh' > /dev/null || true'
		sleep 30
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /srv/jboss*'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss /home/InComm/CMS'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${tmpLocation}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${dataLocation}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/inComm_VMS_JBoss*'
		scp -i ~/.ssh/pipeline -q ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown jboss:jboss ${artifactDeploymentLoc}/${artifactName}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 750 ${artifactDeploymentLoc}/${artifactName}'
	"""
	//ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c jboss && /bin/sh /home/InComm/CMS/jboss_startup_host_7.3.sh > /dev/null'
			//ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su jboss -c '/home/InComm/CMS/jboss_startup_host_7.3.sh' > /dev/null'
        //if (targetEnv!='backup-prod'){
    sh """
    	ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss '/home/InComm/CMS/jboss_startup_host_7.3.sh' > /dev/null'
		sleep 80
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/find ${artifactDeploymentLoc} -type f -name ${artifactName}*.failed | wc -l' > commandresult
		sleep 10
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
			timeout(10) {
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
    //}else{
    //    echo "Deployed to Contingency Server"
    //}
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
