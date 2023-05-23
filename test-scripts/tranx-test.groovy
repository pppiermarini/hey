import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*



@Library('pipeline-shared-library') _
credsName = "scm_deployment"
emailDistribution="rkale@incomm.com"

mount="~/trnsx-uat-deploy"
now = new Date()
tStamp = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
archiveFolder="archive_${tStamp}"
env="uat"

snyk="/usr/bin/snyk-linux"
snykHtml="/usr/bin/snyk-to-html-linux"
scanCodeDir="reports-code"

trx_backend="trnx-backend.zip"
trx_prod="trnx-prod.zip"

tmp_path="/mnt/resource/blobfusetmp"
config_file="/root/fuse_connection.cfg"

file_path_backend="/root/trnsx-uat-deploy/tranx-backend.zip"
file_path_prod="/root/trnsx-uat-deploy/tranx-prod.zip"


approvalData = [
    'operators': "[fharman,hgashaw,smathialagan,ymilliyard]",
    'adUserOrGroup' : 'fharman,hgashaw,smathialagan,ymilliyard'
    'target_env' : "${env}"
]

node('linux2'){
	cleanWs()
	try { 

		stage('Approval Check') {
           
            getApproval(approvalData)
           
        }

		stage('Checking Mount'){
		echo "Checking the ${mount}"

		sshagent([credsName]) {
			check = sh(script: """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'test -d ${mount} && echo '1' || echo '0''""", returnStdout: true).trim()
				if ("${check}" == '1') {
				echo "The ${mount} is mounted"
				}
				else {
				echo "The ${mount} is not mounted"
				echo "proceeding to mount.."
				sh(script: """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'blobfuse ${mount} --tmp-path=${tmp_path} --config-file=${config_file} -o attr_timeout=240 -o entry_timeout=240 -o negative_timeout=120'""", returnStdout: true).trim()
				}	
			}
		}

		stage('Snyk Test'){
		echo "Getting the zip from ${env}"
		//spscmbuild07v.unx.incommtech.net
		//10.40.6.230
		sshagent([credsName]) {
			sh(script: """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'scp -o StrictHostKeyChecking=no root@spscmbuild07v.unx.incommtech.net:${file_path_backend} ${WORKSPACE}'""", returnStdout: true).trim()
			sh(script: """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'scp -o StrictHostKeyChecking=no root@spscmbuild07v.unx.incommtech.net:${file_path_prod} ${WORKSPACE}'""", returnStdout: true).trim()

			}
		echo "Unzipping the file for scan"
		sh """unzip -q ${WORKSPACE}/*.zip
			  ls -ltra
		"""

			/*
			echo "initiate the syk auth for Tranx"
			//@TODO: WORK WITH SEC TEAM TO GET CREDS
			withCredentials([usernamePassword(credentialsId: '', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
				sh """
				"""   
			}
			sh"${snyk} test --json --severity-threshold=low | ${snykHtml} -o Tranx-code.html"*/


		}

		/*
		stage('Publish Snyk Report') {
			dir("${scanCodeDir}") {
				sh """
				mv ${WORKSPACE}/Tranx-code.html ${WORKSPACE}/${scanCodeDir}
				ls -ltra 
				"""
			}

			publishHTML (target : [allowMissing: false,
	 			alwaysLinkToLastBuild: true,
	 			keepAll: true,
	 			reportDir: 'reports-container',
	 			reportFiles: "Tranx-code.html",
	 			reportName: "Report for Tranx",
	 			reportTitles: "The Report of Tranx"])
		}

		stage('Ansible Deploy') {
			sshagent([credsName]) {
					sh """ssh -q -o StrictHostKeyChecking=no root@10.114.144.132 'ansible PARAMS'"""
				}
		}*/


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

///// The End