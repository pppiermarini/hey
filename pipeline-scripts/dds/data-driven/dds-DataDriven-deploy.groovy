import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// GLOBALS/UI PARAMS
credsName = "scm_deployment"
gitCredentials = "scm-incomm"
chmod = "755"
targetsAll = null
pipelineAction = "deploy"  // input from job or yml for deploy or chnge request etc...(only one action)
targetEnv = "${TARGET_ENV}"
ymlRepository = "${YML_REPOSITORY}" // NOT THE DEV'S CODE, this is where DD Info is stored ( what is DD)
ymlBranch = "${YML_BRANCH}"


emailDistribution = "pchattaraj@incomm.com dstovall@incomm.com"

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
configFolderLoc = ""
user = ""
group = ""
warFolderName = ""
newWarFolderName = ""
gitDeployModule = ""
deployCommonConfig = null

// APPROVAL DATA
approvalData = [
    'operators': "[vchavva, dstovall, ppiermarini, jrivett, pchattaraj]",
    'adUserOrGroup' : 'vchavva, dstovall, ppiermarini, jrivett, pchattaraj',
    'target_env' : "${targetEnv}"
]

// TARGETS
targets_dds_lle = [
    'dev':  ['10.42.17.153'],
    'qa':   ['10.42.80.201'],
	'uat': ['10.42.49.136']
]

targets_Edge_Mtl = [
	'PROD_1': ['?'],
	'PROD_2': ['?'],
	'PROD_3': ['10.40.4.5'],
	'PROD_4': ['10.40.4.6']
]

node('linux1'){
    // PIPELINE STARTS HERE
    try {
        // get approval if high-level environment
        stage('Approval Check') {
            if (targetEnv == "PROD_1" || targetEnv == "PROD_2" ||targetEnv == "PROD_3") {
                	getApproval(approvalData)
            }
        }

    	stage('Github Checkout') {
            echo 'Cleaing workspace'
            cleanWs()
            echo 'Checking out GitRepo containing the YML'
            githubCheckout(gitCredentials, ymlRepository, ymlBranch)

        }

        // determines which targets to use based on results of defineTargets()
        stage('Define Targets') {
            defineTargets()
            echo "Target Env: ${targetEnv}: All Targets:"
            println  targetsAll[targetEnv]
        }

        // Reading data-driven pipeline values from YML file
    	stage('Read YAML file') {
            echo 'Reading dataDriven.yml file'
            projectProperties = readYaml (file: 'digitaldeliveryDataDriven.yml')
            if (projectProperties == null) {
                throw new Exception("dataDriven.yml not found in the project files.")
            }

            // TODO: Ensure the values in this sanity check match the values in YML
            echo "Sanity Check"
            if (projectProperties.artifactInfo == null || projectProperties.deployment == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        }


        // Get artifact from artifactory
        stage('Get Artifact') {
            // Assign relevant values from YML
            repoId = projectProperties.artifactInfo.repoId
            groupId = projectProperties.artifactInfo.groupId 
            artifactId = projectProperties.artifactInfo.artifactId 
            artExtension = projectProperties.artifactInfo.artExtension 
			artifactName = projectProperties.artifactInfo.artifactName
            
			// Select and download artifact
            list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
            echo "the list contents ${list}"
            artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
            parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
            sleep(3)
            artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)
            
			// Renames artifact to generic file name
            echo "the artifact ${artifactId}-${artifactVersion}.${artExtension}"
            //sh "cp -rp ${artifactId}-${artifactVersion}.${artExtension} ${artifactName}"
            sh "ls -ltr" 
		}

        stage("Deployment to ${targetEnv}"){
			// Assign relevant values from YML
			serviceName = projectProperties.deployment.serviceName
			artifactDeploymentLoc = projectProperties.deployment.artifactDeploymentLoc
			configFolderLoc = projectProperties.deployment.configFolderLoc
			instanceLogsLoc = projectProperties.deployment.instanceLogsLoc
			user = projectProperties.deployment.user
			group = projectProperties.deployment.group
			gitDeployModule = projectProperties.deployment.gitDeployModule
			deployCommonConfig = projectProperties.deployment.deployCommonConfig

			deployComponents(targetEnv, targetsAll[targetEnv], "${artifactId}-${artifactVersion}.${artExtension}")
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

def defineTargets() {
    println("${JOB_NAME}")
	// check if LLE and use default envs if so
	if (!target_env.matches(".*prod.*|.*PROD.*")) {
		targetsAll = targets_dds_lle
		return 
	}
    parentFolder = "${JOB_NAME}".split("/")[0].toLowerCase()
    println(parentFolder)
    if (parentFolder ==~ /dds-edge-mtl-data-driven/) {
        targetsAll = targets_Edge-Mtl
    }
    else {
        throw new Exception("No Application Targets found for the defined application")
    }
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
	def asterisk = "*"
	echo "artifactDeploymentLoc  ${artifactDeploymentLoc}"
	echo "configFolderLoc ${configFolderLoc}"
	echo "instanceLogsLoc ${instanceLogsLoc}"
	echo "serviceName ${serviceName}"
	echo "Artifact ${Artifact}"
	sh "ls -ltr"
	
	sshagent([credsName]) {
		// Stop Service
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl stop ${serviceName} > /dev/null'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'ls -ltr /app/edge-mtl/'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${artifactDeploymentLoc}/${artifactId}*.${artExtension}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${configFolderLoc}/*'
			scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/touch ${artifactDeploymentLoc}/${artifactId}.${artExtension} && /bin/ln -fs ${artifactDeploymentLoc}/${artifactId}-${artifactVersion}.${artExtension} ${artifactDeploymentLoc}/${artifactId}.${artExtension}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod 755 ${artifactDeploymentLoc}/${artifactId}*.${artExtension}'
			scp -q -o StrictHostKeyChecking=no config/${asterisk} root@${target_hostname}:${configFolderLoc}/
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${artifactDeploymentLoc}'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl start ${serviceName} > /dev/null'
			
		"""
	}

}
