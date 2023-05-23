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
emailDistribution="ppiermarini@incomm.com"

gitConfigRepo = "https://github.com/InComm-Software-Development/FSAPI_Prod_ConfigFiles.git"
gitConfigFolder = "release_configs"
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

//Application array of pom and yml locations, they must be present in the same location of source control
//If pom is in root the value is null or else the folder name provided by FSAPI.
applications = [
'apisecurity': [null],
'ccaget': ['CCA_Get'],
'ccaaction': ['CCA_Action'],
'b2b': [null],
'ingordc': [null]
]

// JENKINS UI PARAMS
//appID = "${APP_ID}"

chmod="750"
pipeline_id="${env.BUILD_TAG}"
asterisk="*"
targets = []
//
//https://incomm-payments.atlassian.net/wiki/spaces/FSMW/pages/16724394068/FSAPI+Production+Server+List
targetIDs = [
	'apisecurity': """ 
		dev-a,
		qa-aa,
		qa-bf,
		uat,
		dr
	""",
	'ccaget':""" 
		dev-a,
		qa-aa,
		qa-bf,
		uat
	""",
	'ccaaction': """ 
		dev-a,
		qa-aa,
		qa-bf,
		uat
	""",
	'b2b':""" 
		dev-a,
		qa-ab,
		qa-bf,
		uat-c
	""",
	'ingordc': """
		qa-be
	"""
]

targetAddresses = [
    'dev-a': '10.42.17.242',
    'dev-b': '10.42.16.181',
    'dev-c': '10.42.16.183',
    'dev-d': '10.42.17.244',
    'dev-e': '10.42.17.245',
    'dev-f': '10.42.17.246',
    'dev-g': '10.42.17.247',


    'qa-aa': '10.42.82.107',
    'qa-ab': '10.42.82.108',
    'qa-ac': '10.42.82.109',
    'qa-ad': '10.42.84.113',

    'qa-ba': '10.42.81.160',
    'qa-bb': '10.42.81.163',
    'qa-bc': '10.42.82.221',
    'qa-bd': '10.42.82.223',
    'qa-be': '10.42.82.217',
    'qa-bf': '10.42.82.215',

    'uat-a': '10.42.48.135',
    'uat-b': '10.42.48.136',
    'uat-c': '10.42.48.132',
    'uat-d': '10.42.48.134',
    'uat-e': '10.42.84.113',
    'uat-f': '10.42.84.112'
	

]

/*
	'prod_SPFSSRV10FV':  ['10.41.6.113'],
	'prod_SPFSSRV11FV':  ['10.41.6.114'],DPVTUAPP01FV 10.191.4.28
DPVTUAPP02FV 10.191.4.29
DPVTUAPP03FV 10.191.4.30
	'prod_SPFSSRV12FV':  ['10.41.6.115'],
	'prod_SPFSSRV07FV':  ['10.41.5.53'],
	'prod_SPFSSRV08FV':  ['10.41.5.54'],
	'prod_SPFSSRV09FV':  ['10.41.5.55'],
	'prod_SPFUSSRV02FV':  ['10.41.4.181'],
*/


approvalData = [
    'operators': "['ppiermarini,dkumar,ssanka,jkumari,mkhan,schennamsetty,rsher']",
    'adUserOrGroup' : 'ppiermarini,dkumar,ssanka,jkumari,mkhan,schennamsetty,rsher',
    'target_env' : "${targetEnv}"
]

// Multi purpose variable that holds the location of pom/data driven file and sets the target envs from userinput per application
folderBuildName = applications[appID][0]

//Timestamp for archiveFolder
now = new Date()
tStamp = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
archiveFolder="archive_${tStamp}"

//@TODO: Open FWs, for linux 2: https://incomm-payments.atlassian.net/wiki/spaces/FSMW/pages/16829711680/FSAPI+Lower+Level+Environments (Go all the way down)
node('linux1'){
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
		//Testing

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
			if((targetEnv.toLowerCase().contains("prod")) || (targetEnv.toLowerCase().contains("dr"))) {
				checkoutConfigs()
				sh "ls -ltr && pwd"
				sh "ls -ltr release_configs/incomm-apisecurity/config"
				
				deployComponentsHLE(targetEnv, server_list, "${projectProperties.deployInfo.artifactName}")
			} else {
            
			deployComponentsLLE(targetEnv, targets, "${projectProperties.deployInfo.artifactName}")
			}
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
		//emailDistribution = projectProperties.emailNotification
	}

    //echo "Sanity Check"
	//@TODO: Figure out a better way of checking null values in readYaml 
          //  if (projectProperties.buildInfo.artifactId == null || projectProperties.buildInfo.repoId == null || projectProperties.buildInfo.groupId == null ||
		//	projectProperties.deployInfo.common == null || projectProperties.deployInfo.commonLoc == null || projectProperties.deployInfo.artExtension == null || 
		//	projectProperties.deployInfo.artifactName == null || projectProperties.deployInfo.artifactDeploymentLoc == null || projectProperties.deployInfo.propertiesDeploymentLoc == null ||
		 //   projectProperties.deployInfo.userhle == null || projectProperties.deployInfo.srcPropertieshle == null == null || projectProperties.deployInfo.grouphle == null || 
		//	projectProperties.deployInfo.propArchive == null || projectProperties.deployInfo.serviceName == null || projectProperties.deployInfo.projectName == null) {
           //     throw new Exception("Please fill in the null values: ${projectProperties}")
          //  }
	echo "All yaml attributes: ${projectProperties}"

}



def defineTargets() {

    println("p${JOB_NAME}p")
    println("${JOB_NAME}".split("/")[0].toLowerCase())

	echo "Initial targets: ${targets}"
    if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /debug-lle/) {
		echo "LLE env deploy"
		targetAddresses.each {
            key,value -> 
            println "Key:" + key + "Value:" + value
            
            if(targetIDs[APP_ID].contains(key) && key.contains(targetEnv)) {
                targets.add(value)
            }
          
        }
		echo "Current targets: ${targets}"
     
    } else if("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /fsapi-datadriven-hle/){
		
		echo "Production fsapi-datadriven-hle deploy"
		server_list= SERVER_LIST.split(",")
          
		echo "Current targets: ${targets}"
	} else {
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
	
	serviceName = projectProperties.deployInfo.serviceName
	appID = projectProperties.deployInfo.appID

	if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /fsapi-datadriven-lle/) {
		if (targetEnv == 'qa-a' || targetEnv == 'qa-b') {
			srcProperties = projectProperties.deployInfo.srcPropertieslle //@TODO: Add Key value pairs
			srcProperties = srcProperties + targetEnv.split('-')[0] + targetEnv.split('-')[1][0] + "/"

		} else {
			
			srcProperties = projectProperties.deployInfo.srcPropertieslle + targetEnv + "/"
		}
		echo "Source prop value: ${srcProperties}"
		user = projectProperties.deployInfo.userlle //@TODO: Add Key value pairs
		group = projectProperties.deployInfo.grouplle //@TODO: Add Key value pairs
		propArchive = projectProperties.deployInfo.propArchive
	
	} else if ("${JOB_NAME}".split("/")[0].toLowerCase() ==~ /fsapi-datadriven-hle/){
		// create the appname to get the properties
		
		srcProperties = "incomm-${appID}"
		propArchive = projectProperties.deployInfo.propArchivehle
		user = projectProperties.deployInfo.userhle
		group = projectProperties.deployInfo.grouphle
		echo "CONFIG SOURCE :  ${srcProperties}"
	} 
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

		githubCheckout(gitCreds,gitConfigRepo,gitConfigBranch)
	}

	//@TODO: create a global variable that holds the release version -> Seshu and Dwiz Production requirement for reverting and deployment config paramteric string, srcPropertiesHLE
}

def deployComponentsLLE(envName, targets, Artifact){
	echo "deployComponentsLLE"
	echo "${targets}"
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deployLLE(it, artifactDeploymentLoc, Artifact) } ]
	}
	parallel stepsInParallel
	
	//targets.each {
	//	println "Item: $it"
	//	deploy(it, artifactDeploymentLoc, Artifact, envName)
	//}
	
}

def deployLLE(target_hostname, artifactDeploymentLoc, Artifact) {
	echo "${target_hostname}, ${artifactDeploymentLoc}, ${Artifact}"

	echo " the target LLE is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl stop ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mkdir -p ${propArchive} && mkdir -p ${propArchive}/${archiveFolder}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -rp ${propertiesDeploymentLoc}/* ${propArchive}/${archiveFolder}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${propertiesDeploymentLoc}/*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}
		scp -q -o StrictHostKeyChecking=no ${srcProperties}/${asterisk}.properties root@${target_hostname}:${propertiesDeploymentLoc}"""
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


def deployComponentsHLE(envName, server_list, Artifact){
	
	
	server_list.each {
	    echo "targets dot each"
		println "Item: $it"
		deployHLE(it, artifactDeploymentLoc, Artifact)
	}
	
}

def deployHLE(target_hostname, artifactDeploymentLoc, Artifact) {
	echo "${target_hostname}, ${artifactDeploymentLoc}, ${Artifact}"

	echo " the target HLE is: ${target_hostname}"
	sshagent([credsName]) {
		sh """
		echo 'stop service:  ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'systemctl daemon-reload && systemctl stop ${serviceName}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'mkdir -p ${propArchive} && mkdir -p ${propArchive}/${appID}/${archiveFolder}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'cp -rp ${propertiesDeploymentLoc}/* ${propArchive}/${appID}/${archiveFolder}'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/rm -rf ${propertiesDeploymentLoc}/*'
		scp -q -o StrictHostKeyChecking=no ${Artifact} root@${target_hostname}:${artifactDeploymentLoc}
		scp -q -o StrictHostKeyChecking=no ${gitConfigFolder}/${srcProperties}/config/${TARGET_ENVS}/${asterisk}.properties root@${target_hostname}:${propertiesDeploymentLoc}"""

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


//
//  I use these empty_deployLLE and empty_deployHLE when debugging and I don't
//want to actually deploy anything.
def empty_deployLLE(target_hostname, artifactDeploymentLoc, Artifact) {
	echo "${target_hostname}, ${artifactDeploymentLoc}, ${Artifact}"

	sshagent([credsName]) {
		
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname -i && hostname'	
		"""

	}
}

def empty_deployHLE(target_hostname, artifactDeploymentLoc, Artifact) {
	echo "${target_hostname}, ${artifactDeploymentLoc}, ${Artifact} PUPPY"
     sh "ls -ltr && pwd"
	 
	 sh "ls -ltr ${gitConfigFolder}/${srcProperties}/config/${TARGET_ENVS}"
	 
	sshagent([credsName]) {
		
		sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname -i && hostname'	
		"""

	}
}
///// The End
