import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

//#################
// VTS-WebServices
//#################

@Library('pipeline-shared-library') _


//globals

currentBuild.result = "SUCCESS"
emailDistribution="rkale@incomm.com "

now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

targets = [
    'dev':  ['Dev1-Box','Dev2-Box'],
    'qa':   ['QA1-Box','QA2-Box'],

]

node('linux'){
	try { 
		cleanWs()
		if ("${targetEnv}" == 'qa') {

			stage('Approval to QA') {
				def message = "Approve deploy to QA?"

    			timeout(time: 30, unit: 'SECONDS') {
      				def qaInput = input(
      				id: 'qaInput', message: "$message", parameters: [
      				[$class: 'TextParameterDefinition', defaultValue: 'Approve', description: 'To Proceed, type Approve', name: 'Approve the build to QA']
      			])
      			if (qaInput.indexOf('Approve') == -1) {
        			currentBuild.result = 'ABORTED'
        			error('Deployment aborted, no approval given')
      			}
      			else {
      				echo "${targetEnv}"
      				myEnv(targetEnv)
      				//myEnv(targetEnv,targets[targetEnv])
      			}
    		}
    	}

	}

	else {
		stage("Deploying to ${targetEnv}"){
		//myEnv(targetEnv,targets[targetEnv]))
		echo "${targetEnv}"
		myEnv(targetEnv)

		}
	}


	} catch (exc) {

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


void myEnv(targetEnv) {

echo "${targetEnv} and ${targets[targetEnv]}"
}

///// The End