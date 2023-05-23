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
	'dputavms01fv': ['dputavms01fv'],
	'dputavms02fv': ['dputavms02fv'],
	'dputavms03fv': ['dputavms03fv'],
	'dpvmsaps01fv': ['dpvmsaps01fv'],
	'dpvmsaps02fv': ['dpvmsaps02fv'],
	'dpvmsaps07fv': ['dpvmsaps07fv'],
	'dpvmsaps08fv': ['dpvmsaps08fv'],
	'dpvmsaps09fv': ['dpvmsaps09fv'],
	'dpvmsaps10fv': ['dpvmsaps10fv'],
	'dpvmsaps11fv': ['dpvmsaps11fv'],
	'dpvmsaps17fv': ['dpvmsaps17fv'],
	'dpvmsaps18fv': ['dpvmsaps18fv'],
	'dpvmsaps31fv': ['dpvmsaps31fv'],
	'dpvmsaps36fv': ['dpvmsaps36fv'],
	'splvmsaps66fv': ['splvmsaps66fv'],
	'splvmsaps67fv': ['splvmsaps67fv'],
	'splvmsaps68fv': ['splvmsaps68fv'],
	//'PROD-POOL1': ['spvmsaps01fv','spvmsaps02fv','spvmsaps03fv','spvmsaps07fv','spvmsaps11fv','spvmsaps12fv','spvmsaps17fv','spvmsaps10fv'],
	//'PROD-POOL2': ['spvmsaps04fv','spvmsaps08fv','spvmsaps13fv','spvmsaps14fv','spvmsaps18fv', 'spvmsaps21fv','spvmsaps22fv','spvmsaps23fv','spvmsaps24fv'],
	//'PROD-POOL3': ['spvmsaps05fv','spvmsaps06fv','spvmsaps09fv','spvmsaps15fv','spvmsaps16fv','spvmsaps19fv','spvmsaps20fv','spvmsaps25fv','splvmsaps56fv','splvmsaps57fv','splvmsaps58fv','splvmsaps59fv'],
	//'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167','10.41.5.17','10.41.5.21','10.41.5.227'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168','10.41.5.22','10.41.5.23','10.41.5.228'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.188','10.41.7.189','10.41.7.190','10.41.7.191','10.41.7.192'],	
	//'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235',,'10.41.5.17','10.41.5.21','10.41.5.227'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.5.22','10.41.5.23','10.41.5.228'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.188','10.41.7.189','10.41.7.190','10.41.7.191','10.41.7.192'],
	'PROD-POOL1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167','10.41.5.17','10.41.5.21','10.41.5.227'],
	//'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168','10.41.5.22','10.41.5.23','10.41.5.228'],
	//'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.188','10.41.7.189','10.41.7.190','10.41.7.191','10.41.7.192','10.41.7.193','10.41.7.194','10.41.7.195','10.41.7.196'],
	'PROD-POOL2': ['10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168','10.41.5.22','10.41.5.228'],
	'PROD-POOL3': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171','10.41.5.18','10.41.5.19','10.41.5.20','10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.5.229','10.41.5.230','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.188','10.41.7.189','10.41.7.190','10.41.7.191','10.41.7.192','10.41.7.193','10.41.7.194','10.41.7.195','10.41.7.196'],
	'redemption-batch-pool1': ['10.41.5.11','10.41.5.16','10.41.5.235','10.41.7.141','10.41.7.146','10.41.7.167','10.41.5.12','10.41.5.231','10.41.7.137','10.41.7.142','10.41.7.163','10.41.7.168','10.41.5.20'],
	'redemption-pool2': ['10.41.5.13','10.41.5.14','10.41.5.15','10.41.5.232','10.41.5.233','10.41.5.234','10.41.7.138','10.41.7.139','10.41.7.140','10.41.7.143','10.41.7.144','10.41.7.145','10.41.7.164','10.41.7.165','10.41.7.166','10.41.7.169','10.41.7.170','10.41.7.171'],
	'activation-csd-b2b-pool1': ['10.41.5.17','10.41.5.21','10.41.5.22','10.41.5.23','10.41.5.24','10.41.5.25','10.41.5.26','10.41.7.172','10.41.7.173','10.41.7.174','10.41.7.175','10.41.7.176','10.41.7.177','10.41.7.178','10.41.7.179','10.41.5.227','10.41.5.228','10.41.5.229','10.41.5.230'],
	'activation-csd-b2b-pool2': ['10.41.5.18','10.41.5.19','10.41.7.180','10.41.7.181','10.41.7.182','10.41.7.183','10.41.7.184','10.41.7.185','10.41.7.186','10.41.7.187','10.41.7.188','10.41.7.189','10.41.7.190','10.41.7.191','10.41.7.192','10.41.7.193','10.41.7.194','10.41.7.195','10.41.7.196']
	
]
//RITM0627970 splvmsaps56fv – 10.41.7.172
//,'spvmsaps13fv','spvmsaps14fv','spvmsaps18fv', 'spvmsaps21fv', 'spvmsaps22fv','spvmsaps23fv','spvmsaps24fv'

//splvmsaps57fv – 10.41.7.173
//splvmsaps58fv – 10.41.7.174
//splvmsaps59fv – 10.41.7.175

notificants = "rgadipalli@incomm.com ppiermarini@incomm.com"

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
FPP_Home="/home/InComm/FPP"

approvalData = [
	'operators': "[ppiermarini,ppattabiraman,vhari,rgadipalli,nswamy,sminuku]",
	'adUserOrGroup' : 'ppiermarini,ppattabiraman,vhari,rgadipalli,nswamy,sminuku',
	'target_env' : "${targetEnv}"
]
node('linux'){
	
currentBuild.result = "SUCCESS"

	echo "inputAction ${inputAction}"
	echo ""
	echo ""
	stage('Approval'){
		getApproval(approvalData)        	
	}
		
	stage("Server Stop-Reboot"){	
		shutdownServers(targetEnv, targets[targetEnv], serviceName)

	}
	

	//	stage("Server Reboot"){	
	//	rebootServersV2(targetEnv, targets[targetEnv], serviceName)
	//	sleep(10)
	//}
	
	stage("Server Start"){	
		startupServers(targetEnv, targets[targetEnv], serviceName)
		sleep(10)
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
	targets.each {
	println "Item: $it"
	stop(it, serviceName)
	}
}

def startupServers(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	targets.each {
	println "Item: $it"
	start(it, serviceName)
	}
	
}


def stop(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
		echo target_hostname
		if ((target_hostname == "spvmsaps07fv")||(target_hostname == "spvmsaps08fv")||(target_hostname == "spvmsaps09fv")||(target_hostname == "ssvmsaps96fv")||(target_hostname == "ssvmsaps97fv")||(target_hostname == "ssvmsaps98fv")||(target_hostname == "ssvmsaps99fv")){
		message = "CMS and CSR Service and reboot"
		sshagent([credsName]) {
			sh """
				echo "Stopping JBoss CMS....${target_hostname}"
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c ${cmsJbossHome}/jboss_shutdown_host_7.3.sh > /dev/null || true'		
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${cmsJbossPath}/tmp ${cmsJbossPath}/data'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${cmsJbossProjects}/*.deployed'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${cmsJbossLog}/server.log" ]; then mv ${cmsJbossLog}/server.log ${cmsJbossLog}/server.log.`date +%Y-%m-%d`; fi'
				echo "Stopping JBoss CSR....${target_hostname}"
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c ${csrJbossHome}/jboss_shutdown_csr_7.3.sh > /dev/null || true'		
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${csrJbossPath}/tmp ${csrJbossPath}/data'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${csrJbossProjects}/*.deployed'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${csrJbossLog}/server.log" ]; then mv ${csrJbossLog}/server.log ${csrJbossLog}/server.log.`date +%Y-%m-%d`; fi'
				sleep 5
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
			"""
		}
		} else if ((target_hostname == "spvmsaps35fv")||(target_hostname == "spvmsaps36fv")){
			message = "FPP reboot"
		sshagent([credsName]) {
			sh """
				echo "Stopping  FPP REBOOT...${target_hostname}"
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${FPP_Home}/fpp_shutdown.sh'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${FPP_Home}/cleanup-fpp.sh'
				sleep 5
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'

			"""
		}
		} else if ((target_hostname == "spvmsaps26fv")||(target_hostname == "spvmsaps27fv")||(target_hostname == "spvmsaps28fv")||(target_hostname == "spvmsaps29fv")||(target_hostname == "spvmsaps31fv")||(target_hostname == "spvmsaps32fv")||(target_hostname == "spvmsaps33fv")||(target_hostname == "spvmsaps34fv")) {
			message = "Reboot only"
			sshagent([credsName]) {
				sh """
					echo " REBOOT ONLY.... ${target_hostname}"
					ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
					ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
				"""
			}
		} else {
			message = "CMS Service and reboot"
			sshagent([credsName]) {
			sh """
				echo "Stopping JBoss CMS Host.....  ${target_hostname}"
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c ${cmsJbossHome}/jboss_shutdown_host_7.3.sh > /dev/null || true'		
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -rf ${cmsJbossPath}/tmp ${cmsJbossPath}/data'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'rm -f ${cmsJbossProjects}/*.deployed'
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'if [ -f "${cmsJbossLog}/server.log" ]; then mv ${cmsJbossLog}/server.log ${cmsJbossLog}/server.log.`date +%Y-%m-%d`; fi'
				sleep 5
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
			"""
			}
		}

} //stop 


def rebootV2(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
		echo target_hostname
		if ((target_hostname == "spvmsaps07fv")||(target_hostname == "spvmsaps08fv")||(target_hostname == "spvmsaps09fv")||(target_hostname == "ssvmsaps96fv")||(target_hostname == "ssvmsaps97fv")||(target_hostname == "ssvmsaps98fv")||(target_hostname == "ssvmsaps99fv")){
		message = "CMS and CSR Service and reboot"
		sshagent([credsName]) {
			sh """
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
			"""
		}
		} else if ((target_hostname == "spvmsaps35fv")||(target_hostname == "spvmsaps36fv")){
			message = "FPP reboot"
		sshagent([credsName]) {
			sh """

				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'

			"""
		}
		} else if ((target_hostname == "spvmsaps26fv")||(target_hostname == "spvmsaps27fv")||(target_hostname == "spvmsaps28fv")||(target_hostname == "spvmsaps29fv")||(target_hostname == "spvmsaps31fv")||(target_hostname == "spvmsaps32fv")||(target_hostname == "spvmsaps33fv")||(target_hostname == "spvmsaps34fv")) {
			message = "Reboot only"
			sshagent([credsName]) {
				sh """
					echo " REBOOT ONLY.... ${target_hostname}"
					ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
					ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
				"""
			}
		} else {
			message = "CMS Service and reboot"
			sshagent([credsName]) {
			sh """

				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
			"""
			}
		}


} //rebootV2


def start(target_hostname, serviceName) {
	
	echo " Starting target is: ${target_hostname}"
		echo target_hostname
	//ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'pgrep -f jboss' > commandresult
    //ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${cmsJbossHome}/jboss_startup_host_7.3.sh -s /bin/sh jboss'
    //ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${csrJbossHome}/jboss_startup_csr_7.3.sh -s /bin/sh jboss'
		if ((target_hostname == "spvmsaps07fv")||(target_hostname == "spvmsaps08fv")||(target_hostname == "spvmsaps09fv")||(target_hostname == "ssvmsaps96fv")||(target_hostname == "ssvmsaps97fv")||(target_hostname == "ssvmsaps98fv")||(target_hostname == "ssvmsaps99fv")){
		
		sshagent([credsName]) {
			sh """
			echo "Starting JBoss CMS....${target_hostname}"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c ${cmsJbossHome}/jboss_startup_host_7.3.sh > /dev/null'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 20'
			
			echo "Starting JBoss CSR....${target_hostname}"
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c ${csrJbossHome}/jboss_startup_csr_7.3.sh > /dev/null'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 20'
			
			"""
		}
		} else if ((target_hostname == "spvmsaps35fv")||(target_hostname == "spvmsaps36fv")){
		sshagent([credsName]) {
			sh """
				echo "Starting  FPP....${target_hostname}"
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'su -c ${FPP_Home}/fpp_startup.sh'
			"""
		}
		} else if ((target_hostname == "spvmsaps26fv")||(target_hostname == "spvmsaps27fv")||(target_hostname == "spvmsaps28fv")||(target_hostname == "spvmsaps29fv")||(target_hostname == "spvmsaps31fv")||(target_hostname == "spvmsaps32fv")||(target_hostname == "spvmsaps33fv")||(target_hostname == "spvmsaps34fv")) {
		//reboot only
		sshagent([credsName]) {
			sh """
				sleep 10
				ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'hostname && hostname -i'
			"""
		}
		} else {
		sshagent([credsName]) {
		sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} '/bin/su - jboss -c ${cmsJbossHome}/jboss_startup_host_7.3.sh > /dev/null'
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'sleep 10'
			
		"""
		}
		}
	
/*def r = readFile('commandresult').trim()
		echo "arr= p${r}p"
		if(r == "1"){
		echo "failed deployment"
		currentBuild.result = 'FAILED'
		} else {
		echo "jboss started"
		}*/
	sleep(5)
} //start


//==================== Below in Development ========================

def rebootServers(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { reboot(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}

def reboot(target_hostname, serviceName) {
	
	echo " the target is: ${target_hostname}"
	sshagent([credsName]) {
	sh """
		echo "charlatan reboot"
		#ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'reboot &'
	"""
	}
} //reboot


def checkCMSLog(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { checkCMS(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}

def checkCMS(target_hostname, serviceName) {
	
		sshagent([credsName]) {
			sh """
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message1}" ${cmsJbossLog}/server.log | wc -l' > commandresult1
			ssh -q -o StrictHostKeyChecking=no root@${target_hostname} 'grep -E "${message2}" ${cmsJbossLog}/server.log | wc -l' > commandresult2
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
		
}//checkCMS


def checkCSRLog(envName, targets, serviceName){
	
	echo "my env= ${envName}"
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { checkCSR(it, serviceName) } ]
	}
	parallel stepsInParallel
	
}

def checkCSR(target_hostname, serviceName) {
	
		sshagent([credsName]) {
			sh """
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
		
}//checkCSR
