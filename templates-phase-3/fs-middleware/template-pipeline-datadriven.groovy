import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

@Field projectProperties


credsName = 'scm_deployment'

targets = [
    'dev':  ['10.42.84.xx'],
    'qa': ['101.xx.xx.xx'],
    'uat':['101.xx.xx.xx'],
    'load':['101.xx.xx.xx']
]


//Global Params
gitCredentials = 'scm-incomm'

emailDistribution = ''

approvalData = [
    'operators': "[ppiermarini,mli]",
    'adUserOrGroup' : 'ppiermarini,mli',
    'target_env' : "${targetEnv}"

]
artifactVersion = ''
artifact = ''
targetList=[]


node('linux1') {

    try {
		
    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo'
            githubCheckout(gitCredentials, gitUrl, gitBranch)
        }

        //Reading the YML values
    	stage('Read YAML file') {
            echo 'Reading hello-demo.yml file'
            projectProperties = readYaml (file: 'pipeline-data/hello-demo.yml')
            if (projectProperties == null) {
                throw new Exception("hello-demo.yml not found in the project files.")
            }
            if (projectProperties.email.emailDistribution != null) {
                emailDistribution = projectProperties.email.emailDistribution
            }

            echo "Sanity Check"
            if (projectProperties.gitInfo.gitYmlFile == null || projectProperties.gitInfo.gitLoc == null ||
             projectProperties.applicationInfo.logLoc == null || projectProperties.applicationInfo.configLoc == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        }
		
		
        stage('Approval Check') {
            if (targetEnv == "uat" || targetEnv == "pre-prod" || targetEnv == "prod" || targetEnv == "load" || targetEnv == "pre-prod-cat1" || targetEnv == "prod-cat1") {
                getApproval(approvalData)
            }
        }


        stage('Get Artifact'){
        if (projectProperties.artifactInfo.artifactId != null ) {

			echo "${projectProperties.artifactInfo.repoId}"
			echo "${projectProperties.artifactInfo.groupId}"
			echo "${projectProperties.artifactInfo.artifactId}"
			
			list = artifactResolverV2(projectProperties.artifactInfo.repoId, projectProperties.artifactInfo.groupId, projectProperties.artifactInfo.artifactId, projectProperties.artifactInfo.extension)
			//echo "the list contents ${list}"
			artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
			parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
			sleep(3)
			artifactWget(projectProperties.artifactInfo.repoId, projectProperties.artifactInfo.groupId, projectProperties.artifactInfo.artifactId, projectProperties.artifactInfo.extension, artifactVersion)
			
			echo "the artifact version is ${artifactVersion}"
			
			artifact = "${projectProperties.artifactInfo.artifactId}-${artifactVersion}.${projectProperties.artifactInfo.extension}"
			echo "artifact = ${artifact}"
			sh "ls -ltr"
	   }
        else {
            echo "Skipping Artifactory Download"
        }

        }


        stage("Deploying to ${targetEnv}") {
			
			if (Deploy == "true") {
				echo "{$targetEnv}"
				deployComponents(targetEnv, targets[targetEnv], artifact)
			}
			else {
				echo "Only a cert update"
			}

        }

    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"

    } finally {

		echo """
				
********************************************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
targetList:				${targetList}
--------------------------------------------------------------------------------
"""

		if(emailDistribution.trim().length() > 0) {
			echo "send notification that deployment has taken place"

			mail 	to:"${emailDistribution}",
				subject:"${JOB_NAME} ${BUILD_NUMBER}",
				body: """
Application version ${artifactVersion} has been deployed from the ${gitBranch} branch.


**************************************************
Build-Node: 			${NODE_NAME}
Jenkins-Build-Number: 	${BUILD_NUMBER}
Jenkins-Build-URL: 		${JOB_URL}
Environment:			${targetEnv}
targetList:				${targetList}
**************************************************\n\n\n
"""
		}
	}		

} //end of node



def deployComponents(envName, targets, Artifact){
    echo "my env= ${envName}"
	echo "${projectProperties.applicationInfo.appLoc}"	
	echo "${projectProperties.applicationInfo.logLoc}"
	echo "${projectProperties.applicationInfo.configLoc}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, projectProperties.applicationInfo.appLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}

def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	echo " the target is: ${target_hostname}"
targetList << target_hostname
	sshagent([credsName]) {
		sh """
			echo "Enter Deployment Steps in this section"
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${projectProperties.applicationInfo.logLoc}/tcserver.pid" ]; then /sbin/service ${projectProperties.applicationInfo.serviceName} stop; fi'
			scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'chown -R ${projectProperties.permissions.user}:${projectProperties.permissions.group} ${artifactDeploymentLoc} && chmod ${projectProperties.permissions.chmod} ${artifactDeploymentLoc}'
			#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${projectProperties.applicationInfo.serviceName} start'
		"""
	}
}