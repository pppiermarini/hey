import hudson.model.*


@Library('pipeline-shared-library') _

credsName = "scm_deployment"

targetEnv = "${server}"

targets = [
	'sslgcivr99fv' : ['sslgcivr99fv'],
	'dplgcivr01fv' : ['dplgcivr01fv'],
	'spgcivr01fv': ['spgcivr01fv'],
	'spgcivr02fv': ['spgcivr02fv'],
	'spgcivr03fv': ['spgcivr03fv'],
	'spgcivr04fv': ['spgcivr04fv'],
	'spgcivr05fv': ['spgcivr05fv'],
	'spgcivr06fv': ['spgcivr06fv'],
	'spgcivr07fv': ['spgcivr07fv'],
	'spgcivr08fv': ['spgcivr08fv'],
	'splgcivr09fv': ['splgcivr09fv'],
	'splgcivr10fv': ['splgcivr10fv'],
	'splgcivr11fv': ['splgcivr11fv'],
	'splgcivr12fv': ['splgcivr12fv'],
	'splgcivr13fv': ['splgcivr13fv'],
	'splgcivr14fv': ['splgcivr14fv'],
	'splgcivr15fv': ['splgcivr15fv'],
]


notificants = "phariharan@incomm.com abeckett@incomm.com"

message = ""
message1 = "ERROR"
message2 = "Could not create connection"


serviceName = "jboss"
optionStop = 
optionStart = 

jbossLog = "/logs/server-log"
jbossLogArchive = "/logs/server-log/archive"
jbossPath = "/srv/jboss-eap-7.2/standalone-JBOSS_APS"
jbossBin = "/srv/jboss-eap-7.2/bin"
jbossProjects = "/srv/incomm-aps/jboss/deploy/projects"


node('linux'){
currentBuild.result = "SUCCESS"

	stage("Jboss Shutdown ${targetEnv}"){
		
		shutdownServers(targetEnv, targets[targetEnv], serviceName)
		sleep(30)
		
	}
	
	stage("Server Reboot ${targetEnv}"){	
	
		rebootServers(targetEnv, targets[targetEnv], serviceName)
		sleep(65)

	}
	
	stage("Jboss Startup ${targetEnv}"){
		sleep(10)
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
	
	stage('Server logfile Check'){
		sleep(10)
		checkLog(targetEnv, targets[targetEnv], serviceName)

    }

	stage('Notify'){

    emailext attachLog: true, 
        to: "${notificants}",
        subject: "Server Reboot Notification for ${targetEnv}-${message}", 
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
		ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${jbossBin}/jboss_startup_aps.sh -s /bin/sh jboss'
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
