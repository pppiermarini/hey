import hudson.model.*


@Library('pipeline-shared-library') _

credsName = "scm_deployment"

targetEnv = "${server}"

targets = [
	'ssvmsaps96fv': ['ssvmsaps96fv'],
	'ssvmsaps97fv': ['ssvmsaps97fv'],
	'ssvmsaps98fv': ['ssvmsaps98fv'],
	'ssvmsaps99fv': ['ssvmsaps99fv'],
	'spvmsaps01fv': ['spvmsaps01fv'],
	'spvmsaps02fv': ['spvmsaps02fv'],
	'spvmsaps03fv': ['spvmsaps03fv'],
	'spvmsaps04fv': ['spvmsaps04fv'],	
	'spvmsaps05fv': ['spvmsaps05fv'],
	'spvmsaps06fv': ['spvmsaps06fv'],
	'spvmsaps07fv': ['spvmsaps07fv'],
	'spvmsaps08fv': ['spvmsaps08fv'],
	'spvmsaps09fv': ['spvmsaps09fv'],
	'spvmsaps10fv': ['spvmsaps10fv'],
	'spvmsaps11fv': ['spvmsaps11fv'],
	'spvmsaps12fv': ['spvmsaps12fv'],
	'spvmsaps13fv': ['spvmsaps13fv'],
	'spvmsaps14fv': ['spvmsaps14fv'],	
	'spvmsaps15fv': ['spvmsaps15fv'],
	'spvmsaps16fv': ['spvmsaps16fv'],
	'spvmsaps17fv': ['spvmsaps17fv'],
	'spvmsaps18fv': ['spvmsaps18fv'],
	'spvmsaps19fv': ['spvmsaps19fv'],
	'spvmsaps20fv': ['spvmsaps20fv'],
	'spvmsaps21fv': ['spvmsaps21fv'],
	'spvmsaps22fv': ['spvmsaps22fv'],
	'spvmsaps23fv': ['spvmsaps23fv'],
	'spvmsaps24fv': ['spvmsaps24fv'],	
	'spvmsaps25fv': ['spvmsaps25fv'],
	'spvmsaps26fv': ['spvmsaps26fv'],
	'spvmsaps27fv': ['spvmsaps27fv'],
	'spvmsaps28fv': ['spvmsaps28fv'],
	'spvmsaps29fv': ['spvmsaps29fv'],
	'spvmsaps30fv': ['spvmsaps30fv'],
	'spvmsaps31fv': ['spvmsaps31fv'],
	'spvmsaps32fv': ['spvmsaps32fv'],
	'spvmsaps33fv': ['spvmsaps33fv'],
	'spvmsaps34fv': ['spvmsaps34fv'],	
	'spvmsaps35fv': ['spvmsaps35fv'],
	'spvmsaps36fv': ['spvmsaps36fv'],
	'PROD-POOL1': ['10.41.5.11','10.41.5.12','spvmsaps07fv','10.41.5.21','10.41.5.22','10.41.5.227','10.41.5.20'],
	'PROD-POOL2': ['10.41.5.13','10.41.5.14','spvmsaps08fv','10.41.5.23','10.41.5.24','10.41.5.228', '10.41.5.231', '10.41.5.232','10.41.5.233','10.41.5.234'],
	'PROD-POOL3': ['10.41.5.15','10.41.5.16','spvmsaps09fv','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.5.235'],
]
//spvmsaps07fv 10.41.5.17 
//spvmsaps08fv 10.41.5.18,
//spvmsaps09fv  10.41.5.19

notificants = "rgadipalli@incomm.com"

message = ""
message1 = "ERROR"
message2 = "Could not create connection"


serviceName = "jboss"
optionStop = 
optionStart = 


cmsJbossLog = "/logs/standalone_host_log"
csrJbossLog = "/logs/standalone_csr_log"
cmsJbossPath = "/srv/jboss-eap-7.3/standalone_host"
csrJbossPath = "/srv/jboss-eap-7.3/standalone_csr"
jbossBin = "/srv/jboss-eap-7.3/bin"
cmsJbossHome="/home/InComm/CMS"
csrJbossHome="/home/InComm/CSR"
cmsJbossProjects="/srv/jboss-eap-7.3/standalone_host/deployments"
csrJbossProjects="/srv/jboss-eap-7.3/standalone_csr/deployments"


approvalData = [
	'operators': "[ppiermarini,ppattabiraman,vhari,rgadipalli]",
	'adUserOrGroup' : 'ppiermarini,ppattabiraman,vhari,rgadipalli',
	'target_env' : "${targetEnv}"
]


node('linux'){
currentBuild.result = "SUCCESS"


	stage('Approval'){
		getApproval(approvalData)        	
	}
		
	stage("Server Reboot ${targetEnv}"){	

		rebootServers(targetEnv, targets[targetEnv], serviceName)
		sleep(5)
	}
	
/*
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
*/
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
<li>JBoss-Version: ${cmsJbossPath}</li>
<li>ERROR Message: ${message}</li>
</ul>
<p>**************************************************</p>\n\n\n
</html>
"""
	}

} //end node



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
		if ((target_hostname == 'spvmsaps07fv')||(target_hostname == 'spvmsaps08fv')||(target_hostname == 'spvmsaps09fv')){
		sh """
			// jboss csd only on 7 8 9
			echo "Stopping JBoss CSD..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${csrJbossHome}/jboss_shutdown_csr_7.3.sh -s /bin/sh jboss'		
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${csrJbossPath}/tmp ${csrJbossPath}/data'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${csrJbossProjects}/*.deployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${csrJbossLog}/server.log" ]; then mv ${csrJbossLog}/server.log ${csrJbossLog}/server.log.`date +%Y-%m-%d`; fi'
		"""
		} else {
			sh """
		//  all server jboss host
			echo "Stopping JBoss Host..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${cmsJbossHome}/jboss_shutdown_host_7.3.sh -s /bin/sh jboss'		
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${cmsJbossPath}/tmp ${cmsJbossPath}/data'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${cmsJbossProjects}/*.deployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${cmsJbossLog}/server.log" ]; then mv ${cmsJbossLog}/server.log ${cmsJbossLog}/server.log.`date +%Y-%m-%d`; fi'
			
			// jboss csd only on 7 8 9
			echo "Stopping JBoss CSD..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${csrJbossHome}/jboss_shutdown_csr_7.3.sh -s /bin/sh jboss'		
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${csrJbossPath}/tmp ${csrJbossPath}/data'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${csrJbossProjects}/*.deployed'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${csrJbossLog}/server.log" ]; then mv ${csrJbossLog}/server.log ${csrJbossLog}/server.log.`date +%Y-%m-%d`; fi'
		"""	
			
		}
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
		if ((target_hostname == 'spvmsaps07fv')||(target_hostname == 'spvmsaps08fv')||(target_hostname == 'spvmsaps09fv')){
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${csrJbossHome}/jboss_startup_csr_7.3.sh -s /bin/sh jboss'
			echo "Starting JBoss CSD..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 10'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'pgrep -f jboss' > commandresult
		"""
		} else {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${cmsJbossHome}/jboss_startup_host_7.3.sh -s /bin/sh jboss'
			echo "Starting JBoss Host..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 10'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'pgrep -f jboss' > commandresult
			echo "Starting JBoss CSD..."
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${csrJbossHome}/jboss_startup_csr_7.3.sh -s /bin/sh jboss'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 10'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'pgrep -f jboss' > commandresult
		"""
		}
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
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message1}" ${cmsJbossLog}/server.log | wc -l' > commandresult1
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message2}" ${cmsJbossLog}/server.log | wc -l' > commandresult2
			
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message1}" ${csrJbossLog}/server.log | wc -l' > commandresult1
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message2}" ${csrJbossLog}/server.log | wc -l' > commandresult2
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