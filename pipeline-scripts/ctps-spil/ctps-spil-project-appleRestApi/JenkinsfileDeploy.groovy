import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


targetenvs="${targetEnv}"
testName="myTest"

//10.42.17.136
//10.42.17.254
targets = [
'dev':  ['10.42.17.254'],
'qa':  ['10.42.80.230']
]

approvalData = [
	'operators': "[ppiermarini,vpilli,vhari,mpalve]",
	'adUserOrGroup' : 'ppiermarini,vpilli,vhari,mpalve',
	'target_env' : "${targetEnv}"
]

artifactDeploymentLoc="D\$\\incomm\\jboss\\deploy\\projects"

configDeploymentLocOne="D\$\\jboss-eap-6.3.0\\standalone-SPL-APL\\configuration"

configDeploymentLocTwo="D\$\\jboss-eap-6.3.0\\modules\\incomm\\lib\\main"

archiveLoc="D\$\\Archive"
Deploy_Config="${Config}"

propOne="standalone.xml"
propTwo="module.xml"

gitFolderEnvs="jboss-eap-standalone"
gitFolderCommon="common\\jboss-eap-modules"


//Adding Git creds here
gitRepository="https://github.com/InComm-Software-Development/ctps-spil-apple-jboss-configs.git"
gitBranch="${Git_Configuration}"
gitCreds="scm-incomm"


//vpilli@incomm.com mpalve@incomm.com
emailDistribution="rkale@incomm.com vhari@incomm.com vpilli@incomm.com mpalve@incomm.com psubramanian@incomm.com"
//General pipeline 

serviceName="JBOSS-SPIL-APL"

pipeline_id="${env.BUILD_TAG}"

//tools
maven="E:\\opt\\apache-maven-3.2.1\\bin\\mvn"


component = "spl-appleRest"


//  <groupId>com.incomm.spl.project.apple.ear</groupId>
//    <artifactId>spl-appleRest</artifactId>
//appleRestApi
//com.incomm.spl.projects
repoId = 'maven-release'
groupId = 'com.incomm.spl.project.apple.ear'
artifactId = 'spl-appleRest'
env_propertyName = 'ART_VERSION'
artExtension = 'ear'
artifactName = ''
artifactBareName = ''

artifactVersion="${artifactVersion}"
//globals
relVersion="null"

//currentBuild.currentResult="SUCCESS"
/*userInput="Promote"
approver = ''
approval_status = '' 
operatorsList = []
Deployment_approval=''
*/

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('windows'){
	try { 
		
		cleanWs()

		stage('Approval Check') {
        	if ((targetEnv == 'pre-prod') || (targetEnv == 'prod') || (targetEnv == 'qa') || (targetEnv == 'uat')) {
        		getApproval(approvalData)
        	}
        }

		//select the artifact 
		
/*
		stage('Approval'){
			wrap([$class: 'BuildUser']) {	
			if((userInput== 'Promote' && targetenvs == 'dev')) {	
                    echo "Inside approval block"
                    def user_deploying = "${BUILD_USER_ID}"
                    //'rkale'
                    operators= "['vpilli', 'mpalve', 'psubramanian', 'wzhan']"
                    try {
                	timeout(time: 90, unit: 'SECONDS'){
                    	Deployment_approval = input message: "Deploying to ${targetEnv}", ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operators', submitterParameter: 'approver'
                    	}
                    }
                    catch (err) {
                    	echo "Build Aborted"
						currentBuild.currentResult = "ABORTED"
                    }
                    echo "${Deployment_approval}"
                    approval_status = "${Deployment_approval['approval_status']}"
                    def operator = "${Deployment_approval['approver']}"
					//String op = operator.toString()

                    if (approval_status == 'Proceed'){
                        echo "Operator is ${operator}"
                        if (operators.contains(user_deploying))
      		            {
                            echo "${operator} is allowed to deploy into ${targetEnv}"
							echo "Proceeding to get Artifact"
		                }
		                else
		                {
		                	currentBuild.currentResult = "FAILURE"

		                    throw new Exception("Throw to stop pipeline as user not in approval list")
		                }
                    }else {
                    	 	currentBuild.currentResult = "ABORTED"

                    throw new Exception("Throw to stop pipeline as user selected abort")
                    }
			}
			else {
				echo "${targetEnv} enviornment selected"
				echo "Proceeding to get Artifact"

			}
			} 
        	
		}*/
		/*

		stage('Approval'){
            			
			if (userInput == 'Promote'){

               if ((userInput== 'Promote' && targetenvs == 'dev'))
                {
                    echo "Inside approval block"
                    operatorsList = "['vpilli', 'mpalve', 'psubramanian', 'wzhan', 'rkale']"
                    def Deployment_approval = input message: 'Deploying to Dev', ok: 'Continue', parameters: [choice(choices: ['', 'Abort', 'Proceed'], description: 'Please confirm deployment', name: 'approval_status')], submitter: 'operatorsList', submitterParameter: 'approver'
                    echo "${Deployment_approval}"
                    approval_status = "${Deployment_approval['approval_status']}"
                    def operatorApprover = "${Deployment_approval['approver']}"
					String op = operatorApprover.toString()

                    if (approval_status == 'Proceed'){
                        echo "Operator is ${operatorApprover}"
                        if (operatorsList.contains(op))
      		            {
                            echo "${operatorApprover} is allowed to deploy into ${targetenvs}"
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
        */

		stage('Get Artifact'){
			echo "deployconfig setu to :" + Deploy_Config
			if (Deploy_Config == 'Y'){
				githubCheckout(gitCreds,gitRepository,gitBranch)
			}
			artifactResolver artifacts: [artifact(artifactId: artifactId, extension: artExtension, groupId: groupId, targetFileName: artifactName, version: artifactVersion)], enableRepoLogging: false, releaseUpdatePolicy: 'always', snapshotUpdatePolicy: 'always'	
		

		}

		stage('Deployment'){
			
			deployComponents(targetEnv, targets[targetEnv], "${artifactId}.${artExtension}", Deploy_Config)

		}

		
	} catch(exc) {
			currentBuild.currentResult="FAILED"
			echo 'ERROR:  '+ exc.toString()
			throw exc
	} finally {
	echo "${currentBuild.currentResult}"
	if (currentBuild.currentResult == "FAILURE"){
		echo "if failure"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	//<li>ServerNames: ${targetEnv}= ${Servers}</li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>ServerNames: ${Servers}</li>
					<li>Component: ${component}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""				
	}
	else if (currentBuild.currentResult == "ABORTED"){
		echo "if aborted"
	//<li>Artifact: <b>${artifactId}-${artifactVersion}.${artExtension}</b></li>
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>ServerNames: ${Servers}</li>
					<li>Component: ${component}</li>
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""					
	}
	else if(currentBuild.currentResult == "SUCCESS"){
		echo "success"
	Servers = targets.get(targetEnv)
    emailext mimeType: 'text/html', attachLog: true, 
        to: "${emailDistribution}",
        subject: "Deploy job: ${JOB_NAME}", 
				body: 
				"""
				<html>
						<p>**************************************************</p>
				<ul>
					<li>STATUS: ${currentBuild.currentResult}</li>
					<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
					<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
					<li>Deployed Environment: ${targetenvs}</li>
					<li>ServerNames: ${Servers}</li>
					<li>Component: ${component}</li>
		
				</ul>
						<p>**************************************************</p>\n\n\n
				</html>
				"""			
	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


def deployComponents(envName, targets, Artifact, Deploy_Config){
	
	echo "my env= ${envName}"
	//def stepsInParallel =  targets.collectEntries {
	//	[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact) } ]
	//}
	//parallel stepsInParallel
	
	targets.each {
		println "Item: $it"
		deploy(it, artifactDeploymentLoc, Artifact, envName, Deploy_Config)
	}
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName, Deploy_Config) {
	//del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\$artifactId*.${artExtension}
	//move \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*.ear \\\\${target_hostname}\\${archiveLoc}
	//	move /Y \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*.ear \\\\${target_hostname}\\${archiveLoc}\\${component}_${nowFormatted}
	//			move /Y \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*.deployed \\\\${target_hostname}\\${archiveLoc}\\${component}_deployed_${nowFormatted}
	// del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*.deployed

	echo " the target is: ${target_hostname}"
	bat """
			powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Stop-Service
			copy /Y \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*.ear \\\\${target_hostname}\\${archiveLoc}\\
			del /F /Q  \\\\${target_hostname}\\${artifactDeploymentLoc}\\${component}*
			dir \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			powershell Start-Sleep -s 4
			echo "deploying ${Artifact}"
			copy /Y ${artifactId}-*.ear \\\\${target_hostname}\\${artifactDeploymentLoc}\\
			powershell Start-Sleep -s 4
           """
	echo "deployconfig set to :" + Deploy_Config
	if (Deploy_Config == 'Y'){
			bat """
			move /Y \\\\${target_hostname}\\${configDeploymentLocOne}\\${propOne} \\\\${target_hostname}\\${archiveLoc}\\${propOne}_${nowFormatted}
			move /Y \\\\${target_hostname}\\${configDeploymentLocTwo}\\${propTwo} \\\\${target_hostname}\\${archiveLoc}\\${propTwo}_${nowFormatted}
			powershell Start-Sleep -s 4
			copy /Y ${envName}\\${gitFolderEnvs}\\${propOne} \\\\${target_hostname}\\${configDeploymentLocOne}\\
			copy /Y ${gitFolderCommon}\\${propTwo} \\\\${target_hostname}\\${configDeploymentLocTwo}\\
			"""
	}else {
		echo "Property files not copied"
	}
	   bat """ 
	   powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
	   """

}

def notifyBuild(recipients) {
//        recipientProviders: [culprits()], 
    emailext attachLog: true, 
        to: recipients,
        subject: "Jenkins: Deploy ${currentBuild.currentResult}: ${env.BUILD_TAG}", 
        body: """STATUS: ${currentBuild.currentResult}
    
    Check console output at ${env.BUILD_URL}\n\n\n"""
}


///// The End