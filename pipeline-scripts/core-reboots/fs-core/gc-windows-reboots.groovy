import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

//credsName = "scm_deployment"

targetEnv = "${server}"

targets = [
	'ssvrtmtl99fv' : ['ssvrtmtl99fv'],
]

//ssvrtmtl99fv' : ['10.41.4.66']
/*
1. Take the server out of load
2. Stop JBoss service (i have provided the commands in the attachment)
3. log into the server and select reboot option
4. Wait for few minutes and log back into the server
5. Delete tmp,data and .deployed
6. Start jboss service
7. Verify server logs to make sure there are no errors. (Path: ssvrtmtl99fv\d$\jboss-eap-6.3\standalone-VRTMTL\log\server.log)
8. If no errors put server back in load .

tmp, data location: (Path: ssvrtmtl99fv\d$\jboss-eap-6.3\standalone-VRTMTL\)
.deployed location: (Path: Path: ssvrtmtl99fv\d$\incomm\jboss\deploy\projects)
*/


notificants = "ppiermarini@incomm.com"

message = ""
message1 = "ERROR"
message2 = "Could not create connection"


serviceName = "jboss"
optionStop = 
optionStart = 

jbossLog = "C: srv\\jboss-eap-6.3\\standalone_gcwebservice\\log"
jbossLogArchive = "\\logs\\server-log\\archive"
jbossPath = "\\srv\\jboss-eap-6.3\\standalone_gcwebservice"
jbossBin = "\\srv\\jboss-eap-6.3\\bin"
jbossProjects = "\\srv\\incomm-gcwebservice\\jboss\\deploy\\shared"

shutdownScript ="jboss_shutdown_cca.sh"
startupScript ="jboss_startup_cca.sh"



node('QAAutomation'){
	
currentBuild.result = "SUCCESS"

	stage("Shutdown ${targetEnv}"){
		power(targetEnv, targets[targetEnv], serviceName)
		//shutdownServers(targetEnv, targets[targetEnv], serviceName)
		sleep(30)
	}
	
	stage("Reboot ${targetEnv}"){	
	
		//rebootServers(targetEnv, targets[targetEnv], serviceName)
		sleep(65)
	}
	
	stage("Startup ${targetEnv}"){
		sleep(10)
		//startupServers(targetEnv, targets[targetEnv], serviceName)

//		def r = readFile('commandresult').trim()
//		echo "returned = ${r}"
//		if(r == "1"){
//			echo "failed deployment"
//			currentBuild.result = 'FAILED'
//		} else {
//			echo "started"
//		}
	}
	
	stage('Checking logfile'){
		sleep(10)
		//checkLog(targetEnv, targets[targetEnv], serviceName)
    }

	stage('Notify'){

    emailext attachLog: true, 
        to: "${notificants}",
        subject: "Server Reboots", 
				body: """
<html>
<p>**************************************************</p>
<ul>
<li>STATUS: ${currentBuild.currentResult}</li>
<li>Jenkins-Build-Number: ${BUILD_NUMBER}</li>
<li>Jenkins-Build-URL: <a href="${JOB_URL}">${JOB_NAME}</a></li>
<li>JBoss-Version: ${jbossPath}</li>
<li>ERROR Message: ${message}</li>
</ul>
<p>**************************************************</p>\n\n\n
</html>
"""
	}

} //end node

//================================================================

def power(envName, targets, serviceName){
	
	echo "my env= ${envName}"
		def stepsInParallel =  targets.collectEntries {
		[ "$it" : { pshell(it, serviceName) } ]
	}
	parallel stepsInParallel

//powershell Get-Service ${serviceName} -ComputerName ${target_hostname} ^| Start-Service
	
}

def shutdownServers(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { stop(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}

def rebootServers(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { reboot(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}

def startupServers(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { start(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}
def checkLog(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { check(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}


def pshell(target_hostname, serviceName) {

	echo " the target is: ${target_hostname}"

	bat """
		powershell Get-Service -ComputerName ${target_hostname}
	"""
}

def stop(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${jbossBin}/${shutdownScript} -s /bin/sh jboss'		
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${jbossPath}/tmp ${jbossPath}/data'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${jbossProjects}/*.deployed'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${jbossLog}/server.log" ]; then mv ${jbossLog}/server.log ${jbossLogArchive}/server.log.`date +%Y-%m-%d`; fi'
	"""
	}
	
}

def reboot(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
	"""
	}
}

def start(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {	
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${jbossBin}/${startupScript} -s /bin/sh jboss'
		echo "Starting JBoss..."
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 10'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'pgrep -f jboss' > commandresult
	"""
	}
def r = readFile('commandresult').trim()
		echo "arr= p${r}p"
		if(r == "1"){
		echo "failed deployment"
		currentBuild.result = 'FAILED'
		} else {
		echo "jboss started"
		}
}

def check(target_hostname, serviceName) {
	
		sshagent([credsName]) {
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message1}" ${jbossLog}/server.log | wc -l' > commandresult1
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message2}" ${jbossLog}/server.log | wc -l' > commandresult2
			"""
		}
		
		def r = readFile('commandresult1').trim()
		echo "arr= p${r}p"
		def y = readFile('commandresult2').trim()
		echo "arr= p${y}p"
		
		if((r >= "1")||(y >= "1")){
			if(r >= "1"){
				echo "Found ERROR in server.log"
				message = "ERROR"
			} else {
				echo "Found -Could not create connection- in the server.log file"
				message = "Could not create connection"
			}
			currentBuild.result = 'FAILED'
		
		} else {
		echo "No server.log error found"
		message = "Found no error in server.log"
		}
		
}
