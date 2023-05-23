import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// GLOBALS
credsName = "scm_deployment"
gitCredentials = "scm-incomm"
chmod = "755"
selectedTargets = null

// UI PARAMS
targetEnv = "${TARGET_ENV}"
gitRepository = "${GIT_REPOSITORY}"
gitBranch = "${GIT_BRANCH}"
configVersion = "${CONFIG_VERSION}"

emailDistribution = "jrivett@incomm.com, mdmdevelopmentteam@incomm.com"

// YML VALUE PLACEHOLDERS
// ARTIFACT INFO
repoId = ""
groupId = ""
artifactId = ""
artExtension = ""
artifactName = ""

// DEPLOYMENT INFO
serviceName = ""
artifactDeploymentLoc = ""
instanceLogsLoc = ""
configDeploymentLoc = ""
user = ""
group = ""
execName = ""
newExecName = ""
configPath = ""
configRepository = ""
deployCommonConfig = null
artifactVersion = ""

projectProperties = null

// APPROVAL DATA
approvalData = [
    'operators': "[glindsey,kroopchand]",
    'adUserOrGroup' : 'glindsey,kroopchand',
    'target_env' : "${targetEnv}"
]

// TARGETS
targets_mdm_lle = [
    'dev':  ['10.42.17.67','10.42.17.64'],
    'qa':   ['10.42.80.26','10.42.80.27'],
    'int1': ['10.42.32.205'],
    'int2': ['10.42.32.206'],
    'int': ['10.42.32.205','10.42.32.206'],
]

targets_mdm_api_svc = [
	'PROD_A_1_2': ['10.40.5.105','10.40.5.114'],
	'PROD_A_3_4': ['10.40.5.115','10.40.5.116'],
	'PROD_A_5_6': ['10.40.5.117','10.40.5.118'],
	'PROD_B_8_9_10': ['10.40.7.63','10.40.7.75','10.40.7.76'],
	'PROD_B_11_12_13': ['10.40.7.77','10.40.7.78','10.40.7.79']
]

// TODO: GET HLE TARGETS FOR WEBAPP UI

node('linux2'){
    // PIPELINE STARTS HERE
    try {
        // get approval if high-level environment
        stage('Approval Check') {
            if (targetEnv == "uat" || targetEnv.matches(".*prod.*|.*PROD.*")) {
                	getApproval(approvalData)
            }
        }

    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo containing the YML'
            githubCheckout(gitCredentials, gitRepository, gitBranch)

        }

        // Reading data-driven pipeline values from YML file
    	stage('Read YAML file') {
			readyml()
			defineAttributes()
        }

        // determines which targets to use based on results of defineTargets()
        stage('Define Targets') {
            defineTargets()
            echo "Target Env: ${targetEnv}: All Targets:"
            println  selectedTargets[targetEnv]
        }

        // Get artifact from artifactory
        stage('Get Artifact') {
			selectArtifact()
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
            
			// Rename artifact to generic file name
			sh "ls -tlar" 
            echo "artifact name before renaming ${artifactId}-${artifactVersion}.${artExtension}"
            sh "cp -rp ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"
            sh "ls -tlar" 
		}

        stage("Deployment to ${targetEnv}"){
			deployComponents(targetEnv, selectedTargets[targetEnv], "${artifactName}")
		}
		
    }//try

    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailv3(emailDistribution, getBuildUserv1())	
	}

}

def readyml() {
	echo 'Reading dataDriven.yml file'
	projectProperties = readYaml (file: 'mdmDataDrivenV2.yml')
	if (projectProperties == null) {
		throw new Exception("dataDriven.yml not found in the project files.")
	}

	// TODO: Ensure the values in this sanity check match the values in YML
	echo "Sanity Check"
	if (projectProperties.artifactInfo == null || projectProperties.deployment == null) {
		throw new Exception("Please fill in the null values: ${projectProperties}")
	}
}

// Assign relevant values from YML
def defineAttributes() {
	serviceName = projectProperties.deployment.serviceName
	artifactDeploymentLoc = projectProperties.deployment.artifactDeploymentLoc
	configDeploymentLoc = projectProperties.deployment.configDeploymentLoc
	instanceLogsLoc = projectProperties.deployment.instanceLogsLoc
	user = projectProperties.deployment.user
	group = projectProperties.deployment.group
	execName = projectProperties.deployment.execName
	newExecName = projectProperties.deployment.newExecName
	deployCommonConfig = projectProperties.deployment.deployCommonConfig

	repoId = projectProperties.artifactInfo.repoId
	groupId = projectProperties.artifactInfo.groupId 
	artifactId = projectProperties.artifactInfo.artifactId 
	artExtension = projectProperties.artifactInfo.artExtension 
	artifactName = projectProperties.artifactInfo.artifactName
	configRepository = projectProperties.deployment.configRepository
	configPath = projectProperties.deployment.configPath
}

def defineTargets() {
    println("artifactId: ${artifactId}")

	// check if LLE and use default envs if so
	if (!targetEnv.matches(".*prod.*|.*PROD.*")) {
		echo 'made it!'
		selectedTargets = targets_mdm_lle
		return
    }
    
	echo 'Selecting High-Level Environment!'
	
    switch("${artifactId}") {
		case "mdm-api-service":
			selectedTargets = targets_mdm_api_svc
			break

		default:
			throw new Exception("No Application Targets found for the defined application")
			break
	}
}

def selectArtifact() {
	// Select and download artifact
	list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
	echo "the list contents ${list}"
	artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
	parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
	sleep(3)
}

def deployComponents(envName, targets, Artifact){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it, artifactDeploymentLoc, Artifact, envName) } ]
	}
	parallel stepsInParallel
	
}


def deploy(target_hostname, artifactDeploymentLoc, Artifact, envName) {
	//echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
		// Stop Service
		sh """
			echo 'Stopping ${serviceName} service!'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${instanceLogsLoc}/tcserver.pid" ]; then /sbin/service ${serviceName} stop; fi'
		"""

		// Always removes old artifact and copies the new one
		sh """
			echo 'Deleting all files in ${artifactDeploymentLoc} !'	
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${artifactDeploymentLoc}/${execName}*'

			echo 'Copying from JENKINS: ${env.WORKSPACE}/${artifactName} to REMOTE: ${target_hostname}:${artifactDeploymentLoc}/${newExecName}.${artExtension} !'	
			scp -q -o StrictHostKeyChecking=no ${env.WORKSPACE}/${artifactName} root@${target_hostname}:${artifactDeploymentLoc}/${newExecName}.${artExtension}
		"""
		
		// Deploys configuration if DEPLOY_CONFIG is true
		if (("${DEPLOY_CONFIG}")) {
			sh """
				echo 'copying config from JENKINS: ${configPath}/${configVersion} to REMOTE: ${target_hostname}:${configDeploymentLoc}
				scp -o StrictHostKeyChecking=no -r ${configPath}/${configVersion} root@${target_hostname}:${configDeploymentLoc}
			"""
		}

		// CHMOD and Start service
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${configDeploymentLoc}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${artifactDeploymentLoc}/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${configDeploymentLoc}/'
			echo 'Restarting service: ${serviceName} !'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/sbin/service ${serviceName} start'
		"""
	}

}