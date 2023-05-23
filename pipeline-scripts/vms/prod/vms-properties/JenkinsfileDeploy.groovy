import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

userInput="Promote"
//targetEnv="${targetEnv}"g
targetEnv="${target_env}"

testName="myTest"

targets = [
    'dev':  ['10.42.16.191'],
    'qaa':   ['10.42.82.110'],
	'qab':   ['10.42.81.17'],
	'uat':   ['10.42.49.215'],
    	'stg':  ['10.41.5.96','10.41.5.97','10.41.5.98','10.41.5.99'],
	//'backup-prod':  ['10.41.5.240'],
	//'backup-prod':  ['10.41.5.240','splvmsaps91fv','splvmsaps92fv','splvmsaps93fv','splvmsaps94fv','splvmsaps95fv','splvmsaps96fv','splvmsaps97fv','splvmsaps98fv','splvmsaps99fv','splvmsaps100fv'],
	'backup-prod':  ['splvmsaps77fv'],
	'DenverPOOL':  ['10.191.5.11','10.191.5.12','10.191.5.17','10.191.5.18','10.191.5.19','10.191.5.20','10.191.5.21','10.191.5.227','10.191.5.228'],
	//'PROD-POOL1': ['10.41.5.11','10.41.5.17','10.41.5.21','10.41.5.227'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.22','10.41.5.23','10.41.5.228'],
	//'PROD-POOL3': ['10.41.5.16','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.5.231','10.41.5.232','10.41.5.233','10.41.5.234','10.41.5.235','10.41.7.137','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.141','10.41.7.142','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.146','10.41.7.163','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.167','10.41.7.168','10.41.7.169','10.41.7.170','10.41.7.171','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181'],
	'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167','10.41.5.17','10.41.5.21','10.41.5.227'],
	'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168','10.41.5.22','10.41.5.228'],
	'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.193','10.41.7.194','10.41.7.195','10.41.7.196'],
	//'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171'],
	'SPIL-ACTIVATION-POOL1':  ['10.41.7.188','10.41.7.189','10.41.7.190'],
	'SPIL-ACTIVATION-POOL2':  ['10.41.7.191','10.41.7.192','10.41.7.197','10.41.7.198','10.41.7.199','10.41.7.200','10.41.7.201','10.41.7.202','10.41.7.203','10.41.7.204','10.41.7.205','10.41.7.206'],
]
// RITM0627970
// ,'10.191.5.245','10.191.5.246'
//Please add below 4 new servers to the existing pipelines under PROD-POOL3
//Splvmsaps56fv – 10.41.7.172
//Splvmsaps57fv – 10.41.7.173
//Splvmsaps58fv – 10.41.7.174
//Splvmsaps59fv – 10.41.7.175
//RITM0642067
//splvmsaps60fv	10.41.7.176 
//splvmsaps61fv	10.41.7.177
//splvmsaps62fv	10.41.7.178
////splvmsaps63fv	10.41.7.179
//splvmsaps64fv	10.41.7.180
//splvmsaps65fv	10.41.7.181

emailDistribution="vhari@incomm.com rgadipalli@incomm.com ppiermarini@incomm.com vstanam@incomm.com"
//General pipeline 

//artifactDeploymentLoc ="/srv/jboss-eap-6.3/standalone_host/deployments"
//serviceName="jboss-as-standalone_host"
//pipeline_id="${env.BUILD_TAG}"
//tmpLocation="${artifactDeploymentLoc}/tmp"
//dataLocation="${artifactDeploymentLoc}/data"
//pidLocation="/var/run/jboss"

//tools
maven="/opt/apache-maven-3.2.1/bin/mvn"

///Artifact Resolver	input specifics
repoId = 'maven-release'
groupId = 'com.incomm.vms.host'
artifactId = 'host'
env_propertyName = 'ART_VERSION'
artExtension = 'war'
artifactName = 'inComm_VMS_JBoss.war'

gitRepository="https://github.com/InComm-Software-Development/vms-properties.git"
//gitBranch="${prop_branch}"
gitCreds="scm-incomm"

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
				artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
				 def prop_branch =  "R-${artifactVersion}".split(/\./)[0]
    			echo "Branch Name ${prop_branch}"
				gitBranch="origin/${prop_branch}"
				currentrelver="${prop_branch}"
				echo "current relver ${currentrelver}"
				githubCheckout(gitCreds,gitRepository,gitBranch)
			} else {
				echo "not getting artifact during a release build"
			}
			
		}


		stage("Deployment to ${targetEnv}"){
			echo "This is where we do a bunch of stuff"

			if (userInput == 'Promote'){
                echo targetEnv
				deployconfig(targetEnv, targets[targetEnv], artifactName, artifactVersion)
			} else {
				echo "not deploying during a release build"
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

def deployconfig(envName, targets, Artifact, artifactVersion){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { configdeploy(it, envName, Artifact, artifactVersion) } ]
	}
	parallel stepsInParallel
	
}

def configdeploy(target_hostname, envName, Artifact, artifactVersion){
	sh "pwd && hostname"
	sh "ls -ltra"
	echo "my env =${target_hostname}"
	echo "Deploying Property files for ${artifactVersion}"
   
    propDeploymentLoc = "/home/InComm/CMS/properties/"
    propArchiveLoc =  "/home/InComm/prop_archive/CMS"
    
if ((envName=='stg')||(envName=='backup-prod')||(envName=='DenverPool-1')||(envName=='PROD-POOL1')||(envName=='PROD-POOL2')||(envName=='PROD-POOL3')){
    envName='PROD'
}

  echo " the target is: ${target_hostname}"
	sh """
        ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/mkdir -p ${propArchiveLoc} && /bin/mkdir -p ${currentrelver} && /bin/chown -R jboss:jboss /home/InComm/prop_archive && /bin/chmod -R 755 /home/InComm/prop_archive'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/cp -p -r ${propDeploymentLoc} ${propArchiveLoc}/${currentrelver}'
        scp -i ~/.ssh/pipeline -r ${envName}${propDeploymentLoc} root@${target_hostname}:/home/InComm/CMS/
        ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R jboss:jboss ${propDeploymentLoc}'
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R 750 ${propDeploymentLoc}'
        """
		// ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/echo "${currentrelver}" > ${propArchiveLoc}/currentrelease.txt'
}
def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Build ${currentBuild.result}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.result}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}
