import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

projectProperties = null

// inputs from build with parameters
gitRepository="${SOURCE_REPO}"
gitBranch="${SOURCE_BRANCH}"
gitCreds="scm-incomm"

credsName = 'scm_deployment'

targetEnv="${TARGET_ENVS}"

//General pipeline
emailDistribution="rkale@incomm.com ppiermarini@incomm.com ssanka@incomm.com dkumar@incomm.com schennamsetty@incomm.com"


//For YAML attributes definitions

repoId = ""
groupId = ""
artifactId = ""
common = ""
commonLoc = "" 
artExtension = ""
artifactName = ""
artifactDeploymentLoc = ""
propertiesDeploymentLoc = ""
srcProperties = ""
propArchive = ""
serviceName = ""
user = ""
group = ""
projectName = "" //To hold part of path for srcProperties, Prod only

//Application array of pom and yml locations
applications = [
'FSAPI_Security_SBC3': [null],
'CCA_Get': ['CCA_Get'],
'CCA_Action': ['CCA_Action']
]

// JENKINS UI PARAMS
appID = "${APP_ID}"

chmod="750"
pipeline_id="${env.BUILD_TAG}"

targets = []

//@TODO: Figure out all targets for FSAPI HLE
targetIDs = [
	'FSAPI_Security_SBC3': """ 

	""",
	'CCA_Get':""" 

	""",
	'CCA_Action': """ 

	"""
]

/*
	'prod_SPFSSRV10FV':  ['10.41.6.113'],
	'prod_SPFSSRV11FV':  ['10.41.6.114'],
	'prod_SPFSSRV12FV':  ['10.41.6.115'],
	'prod_SPFSSRV07FV':  ['10.41.5.53'],
	'prod_SPFSSRV08FV':  ['10.41.5.54'],
	'prod_SPFSSRV09FV':  ['10.41.5.55'],
	'prod_SPFUSSRV02FV':  ['10.41.4.181'],
*/
targetAddresses = [


]

//@TODO: Add the details for HLE configs
gitConfigRepo = ""
gitConfigBranch = ""
gitConfigFolder = "hle-configs"


/*

*/


approvalData = [
    'operators': "['ppiermarini,dkumar,ssanka,jkumari,mkhan,schennamsetty']",
    'adUserOrGroup' : 'ppiermarini,dkumar,ssanka,jkumari,mkhan,schennamsetty',
    'target_env' : "${targetEnv}"
]

// Multi purpose variable that holds the location of pom/data driven file and sets the target envs from userinput per application
folderBuildName = applications[APPLICATION_NAME][0]


node('linux'){
	try { 

        cleanWs()

		stage('Approval Check') {
            getApproval(approvalData)
        }

		stage('Checkout'){
			githubCheckout(gitCreds,gitRepository,gitBranch)
		}

        stage('Read YAML file') {
            findYaml(folderBuildName)
        }

		stage('Define Targets') {
            defineTargets()
            
        }

		stage("Define Data Driven Attributes for ${targetEnv}") {
            defineAttributes()
        }

		stage ('Get Artifact'){
			getArtifact()
    	}

		stage("Deploy to ${targetEnv}") {
			if(targetEnv.toLowerCase().contains("prod")) {
				checkoutConfigs()
			}
            deployComponents(targetEnv, targets, "${projectProperties.deployInfo.artifactName}")

        }
		
		
	}     
    
    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailv3(emailDistribution, getBuildUserv1())	
	}
	

} //end of node


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////  Methods and functions and calls Oh My  ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



def findYaml(folderBuildName) {
    if (folderBuildName == null) {
     getYaml()
    }

    else {
    dir("${folderBuildName}") {
     getYaml()
    }
}

}


def getYaml() {
     echo 'Reading fsapiDataDriven.yml file'
    projectProperties = readYaml (file: 'fsapiDataDriven.yml')
    if (projectProperties == null) {
                throw new Exception("fsapiDataDriven.yml not found in the project files.")
    }
	if (projectProperties.emailNotification != null) {
		emailDistribution = projectProperties.emailNotification
	}

    echo "Sanity Check"
	//@TODO: Figure out a better way of checking null values in readYaml 
            if (projectProperties.buildInfo.artifactId == null || projectProperties.buildInfo.repoId == null || projectProperties.buildInfo.groupId == null ||
			projectProperties.deployInfo.common == null || projectProperties.deployInfo.commonLoc == null || projectProperties.deployInfo.artExtension == null || 
			projectProperties.deployInfo.artifactName == null || projectProperties.deployInfo.artifactDeploymentLoc == null || projectProperties.deployInfo.propertiesDeploymentLoc == null ||
		    projectProperties.deployInfo.userhle == null || projectProperties.deployInfo.srcPropertieshle == null == null || projectProperties.deployInfo.grouphle == null || 
			projectProperties.deployInfo.propArchive == null || projectProperties.deployInfo.serviceName == null || projectProperties.deployInfo.projectName == null) {
                throw new Exception("Please fill in the null values: ${projectProperties}")
            }
        
}



def defineTargets() {
    println("${JOB_NAME}")
    
    println("${JOB_NAME}".split("/")[0].toLowerCase())

    if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /fsapi-datadriven-hle/) {
		echo "HLE env deploy"
		targetAddresses.each {
            key,value -> 
            //println "Key:" + key + "Value:" + value
            
            if(targetIDs[APP_ID].contains(key) && key.contains(targetEnv)) {
                targets.add(value)
            }
          
        }
		echo "Current targets: ${targets}"
     
    }
    else {
        throw new Exception("No Application Targets found for the defined application under fsapi-DataDriven-hle")

    }

}


def defineAttributes() {
	echo "Prod level attributes selected"

	repoId = projectProperties.buildInfo.repoId
	groupId = projectProperties.buildInfo.groupId
	artifactId = projectProperties.buildInfo.artifactId
    common = projectProperties.deployInfo.common
	commonLoc = projectProperties.deployInfo.commonLoc 
	artExtension = projectProperties.deployInfo.artExtension
	artifactName = projectProperties.deployInfo.artifactName
	artifactDeploymentLoc = projectProperties.deployInfo.artifactDeploymentLoc
	propertiesDeploymentLoc = projectProperties.deployInfo.propertiesDeploymentLoc
	propArchive = projectProperties.deployInfo.propArchive
	serviceName = projectProperties.deployInfo.serviceName 
    srcProperties = projectProperties.deployInfo.srcPropertieshle //@TODO: Add Key value pairs and Need to add the paramteric path for production config + release version 
	user = projectProperties.deployInfo.userhle //@TODO: Add Key value pairs
    group = projectProperties.deployInfo.grouphle //@TODO: Add Key value pairs
    
	


}

def getArtifact() {
	list = artifactResolverV2(projectProperties.buildInfo.repoId, projectProperties.buildInfo.groupId, projectProperties.buildInfo.artifactId, projectProperties.deployInfo.artExtension)
            
	echo "the list contents ${list}"

	artifactVersion = input message: 'Select the tag to deploy',ok : 'Deploy',id :'tag_id',
    parameters:[choice(choices: list, description: 'Select a tag for this build', name: 'VERSION')]
    sleep(3)
    artifactWget(projectProperties.buildInfo.repoId, projectProperties.buildInfo.groupId, projectProperties.buildInfo.artifactId, projectProperties.deployInfo.artExtension, artifactVersion)
    echo "the artifact version ${artifactVersion}"
	sh "mv ${projectProperties.buildInfo.artifactId}-${artifactVersion}.${projectProperties.deployInfo.artExtension} ${projectProperties.buildInfo.artifactId}.${projectProperties.deployInfo.artExtension}"
    sh "ls -ltr"
}


def checkoutConfigs(){
	dir("${gitConfigFolder}") {
		//@TODO: Add the gitbranch checkout lib here
		//githubCheckout(gitCredentials,gitConfigRepo,gitConfigBranch)
	}

	//@TODO: create a global variable that holds the release version -> Seshu and Dwiz Production requirement for reverting and deployment config paramteric string, srcPropertiesHLE
}

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
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mkdir -p ${propArchive} && mkdir -p ${propArchive}/${archiveFolder}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -rp ${propertiesDeploymentLoc}/* ${propArchive}/${archiveFolder}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${propertiesDeploymentLoc}/*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}
		scp -q -o StrictHostKeyChecking=no ${srcProperties}*.properties root@${target_hostname}:${propertiesDeploymentLoc}"""
		if(common == true) {
		sh"scp -q -o StrictHostKeyChecking=no ${commonProperties}* root@${target_hostname}:${propertiesDeploymentLoc}"
		}

		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod ${chmod} ${artifactDeploymentLoc}/${Artifact}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown ${user}:${group} ${artifactDeploymentLoc}/${Artifact}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chmod -R ${chmod} ${propertiesDeploymentLoc}/'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/chown -R ${user}:${group} ${propertiesDeploymentLoc}/'
		echo 'Restart service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl restart ${serviceName}'
		"""
	}
}


///// The End
