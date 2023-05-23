import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


def repositoryId = 'maven-release'

@Library('pipeline-shared-library') _
credsName = "scm_deployment"
def gitCredentials = 'scm-incomm'
emailDistribution="rkale@incomm.com"


repoID= 'maven-release'
applications = [
'B2B': [null,'incomm-b2b','standalone-b2b.service','B2B','com.incomm','jar'],
'CSS_GET': ['CSS_Get','incomm-cssget', 'standalone-cssget.service', 'CSS_Get', 'com.incomm', 'jar']
]

targetsMaster = [
    'dev-B2B':  ['test1'],
    'qaa-B2B':  ['test3']
]

// Folder Name if exists for pom build locations
//folderBuildName = applications[APPLICATION_NAME][0] 

// Prop file application names
//folderAppName = applications[APPLICATION_NAME][1]

//Service Name of the application being deployed
//serviceName = applications[APPLICATION_NAME][2]

//ArtifactID
//artifactID = applications[APPLICATION_NAME][3]

//GroupID
//groupID = applications[APPLICATION_NAME][4]

//extension
//artifactExtension = applications[APPLICATION_NAME][5] 

//Prop File setup based on env, ex. dev, qaa, qab
//folderEnvLoc = TARGET_ENVIRONMENT.split('-')[0]
//userInputEvalution = "${BUILD_TYPE_EVALUATION}"

//runQualityGate = userInputEvalution.split(',')[0] //RUN_QUALITY_GATE

//promoteArtifact =userInputEvalution.split(',')[0] //PROMOTE_ARTIFACT
//globals
now = new Date()
tStamp = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
archiveFolder="archive_${tStamp}"
repoId = 'maven-release'
groupId = 'com.incomm.opencard'
artifactId = 'OpenCardEntryService'
env_propertyName = 'ART_VERSION'
artExtension = 'jar'
artifactName = ''
artifactBareName = ''

list_branch_modified = ''
Update_Cert= "${Cert}"

node('linux'){
	try { 
            updateCert(targetenvs, targetsMaster[targetenvs], Update_Cert)
    }

 catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {
	
	if (currentBuild.result == "FAILURE"){
		echo "if failure"
		
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else if(currentBuild.result == "SUCCESS"){
		echo "if success"
		emailext attachLog: true,body: "${env.BUILD_URL}",subject: "${env.JOB_NAME} - Build # ${env.BUILD_NUMBER} - ${currentBuild.result}",mimeType: 'text/html',to: "${emailDistribution}"

	}else{
		
		echo "LAST"
		
		//notify ('scm-jenkins', 'slack', 'SUCCESSFUL') This is how you slack
	}
}

} //end of node


def updateCert(envName, targets, Update_Cert){
    
    echo "my env= ${envName}" 


    //targetsMaster[targetenvs]
    deployUpdate(targetsMaster.get(envName)[0], envName, Update_Cert)  
}


def deployUpdate(target_hostname, envName, Update_Cert) {
    echo " the target is: ${target_hostname}"
    echo "Env is: ${envName}"
    envName = envName.toUpperCase()
    echo "UpperCase: ${envName}"
}
///// The End