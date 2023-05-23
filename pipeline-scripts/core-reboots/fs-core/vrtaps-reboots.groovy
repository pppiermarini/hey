import hudson.model.*


@Library('pipeline-shared-library') _

credsName = "scm_deployment"

targetEnv = "${server}"

targets = [
	'sslvrtaps97fv' : ['sslvrtaps97fv'],
	'splvrtaps15fv': ['splvrtaps15fv'],
	'splvrtaps16fv': ['splvrtaps16fv'],
	'splvrtaps17fv': ['splvrtaps17fv'],
	'splvrtaps18fv': ['splvrtaps18fv'],
	'splvrtaps19fv': ['splvrtaps19fv'],
	'splvrtaps20fv': ['splvrtaps20fv'],
	'splvrtaps21fv': ['splvrtaps21fv'],
	'splvrtaps22fv': ['splvrtaps22fv'],
	'splvrtaps23fv': ['splvrtaps23fv'],
	'splvrtaps24fv': ['splvrtaps24fv'],
	'splvrtaps25fv': ['splvrtaps25fv'],
	'splvrtaps26fv': ['splvrtaps26fv'],
	'splvrtaps27fv': ['splvrtaps27fv'],
	'splvrtaps28fv': ['splvrtaps28fv'],
	'splvrtaps29fv': ['splvrtaps29fv'],
]


notificants = "ppiermarini@incomm.com phariharan@incomm.com"

message = ""
message1 = "ERROR"
message2 = "Could not create connection"

/*
RITM0516687
#STOP/START & Clean app logs
alias jstop='cd /srv/jboss-eap-6.3/standalone-JBOSS_APS/log;mv server.log server.log.`date +%Y-%m-%d`; \
cd /srv/jboss-eap-6.3/bin;bash -x ./jboss_shutdown_aps.sh'

alias jstart='cd /srv/jboss-eap-6.3/standalone-JBOSS_APS;rm -fr tmp data; \
cd /srv/incomm-aps/jboss/deploy/projects;rm *.deployed; \
cd /srv/jboss-eap-6.3/bin;bash -x ./jboss_startup_aps.sh'

alias jstatus='ps -fu jboss'

Server log location:
/logs/server-log/server.log
*/

serviceName = "jboss"
optionStop = 
optionStart = 

jbossLog = "/srv/jboss-eap-7.2/standalone-VRTAPS/log"
jbossPath = "/srv/jboss-eap-7.2/standalone-VRTAPS"
jbossBin = "/srv/jboss-eap-7.2/bin"
jbossShared = "/srv/incomm/jboss/deploy/shared"


node('linux'){
currentBuild.result = "SUCCESS"

	stage("Shutdown ${targetEnv}"){
		
		shutdownServers(targetEnv, targets[targetEnv], serviceName)
		sleep(30)
		
	}
	
	stage("Reboot ${targetEnv}"){	
	
		rebootServers(targetEnv, targets[targetEnv], serviceName)
		sleep(65)

	}
	
	stage("Startup ${targetEnv}"){
		
		startupServers(targetEnv, targets[targetEnv], serviceName)

		def r = readFile('commandresult').trim()
		echo "returned = ${r}"
		if(r == "1"){
			echo "failed deployment"
			currentBuild.result = 'FAILED'
		} else {
			echo "started"
		}
		
	}
	
	stage('Checking logfile'){
		sleep(10)
		checkLog(targetEnv, targets[targetEnv], serviceName)

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

def stop(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
	sh """
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${jbossBin}/jboss_shutdown_aps.sh -s /bin/sh jboss'		
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${jbossPath}/tmp ${jbossPath}/data'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${jbossShared}/*.deployed'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${jbossLog}/server.log" ]; then mv ${jbossLog}/server.log ${jbossLog}/server.log.`date +%Y-%m-%d`; fi'
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
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${jbossBin}/jboss_startup_aps.sh -s /bin/sh jboss'
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 3'
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
