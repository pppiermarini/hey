import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


// JENKINS UI PARAMS
targetEnv =             "${TARGET_ENV}"
configBranch =          "${CONFIG_BRANCH}"
configVersion =         "${CONFIG_VERSION}"
skipBackup =            "${SKIP_BACKUP}"
stopService =           "${STOP_SERVICE}"

// Globals
targets = [
    'dev':  ['10.42.17.64', '10.42.17.65', '10.42.17.67'],
    'qa':   ['10.42.80.26','10.42.80.27'],
    'int1': ['10.42.32.205'],
    'int2': ['10.42.32.206'],
    'int': ['10.42.32.205','10.42.32.206']
]

emailDistribution =     "atiruveedhi@incomm.com rkale@incomm.com glodha@incomm.com vhari@incomm.com"
credsName =             "scm_deployment"
gitCreds =              "scm-incomm"
maven =                 "/opt/apache-maven-3.2.1/bin/mvn"
deploymentLocation =    "/var/opt/mdm-batch-app"
backupLocation =        "/home/deploy/backups"
serviceName =           "mdm-batch-app-service"
user =                  "mdmBatchAppUser"
group =                 "mdmBatchAppUser"
repoId =                "maven-all"
artExtension =          "jar"
artifactName =          "mdm-batch-app"
configRepository =      "https://github.com/InComm-Software-Development/mdm-batch-app.git"
groupId =               "com.incomm.mdm"
artifactId =            "mdm-batch-app"

now =                   new Date()
nowFormatted =          now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))
currentBuild.result =   'SUCCESS'

node('linux'){
    try {  
        
        stage('Checkout') {
            githubCheckout(gitCreds,configRepository,configBranch)
        }

        stage('Get Artifact'){
            stage('Get Artifact') {
                echo "GETTING ARTIFACT: ${artifactId}"

                list = artifactResolverV2(repoId, groupId, artifactId, artExtension)
                echo "Available Versions for ${artifactId}: ${list}"
                artifactVersion = singleInputRequested(list)
                sleep(3)
                artifactWget(repoId, groupId, artifactId, artExtension, artifactVersion)

                echo "WORKSPACE CONTENTS AFTER RESOLVING ARTIFACT: "
                sh "ls -thlar"
            }
        }

        stage("Deployment to ${targetEnv}"){ 
            deployComponents(targets[targetEnv])
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
}

def deployComponents(targets){
	def stepsInParallel =  targets.collectEntries {
		[ "$it" : { deploy(it) } ]
	}
	parallel stepsInParallel
}

def deploy(host) {
    echo "======== Deploying to ${host} ========"
    sshagent([credsName]) {

        // Stop Service
        if ("${stopService}" == "true") {
            sh """
                ssh -q -o StrictHostKeyChecking=no root@${host} '/sbin/service ${serviceName} stop'
            """
        }

        // Backup
        // sh """
        //     if [ !${skipBackup} ]
        //     then
        //         ssh -q -o StrictHostKeyChecking=no root@${host} [[ -f  "'${deploymentLocation}/${artifactName}.${artExtension}'" ]]
        //         if [ $? != "1" ]
        //         then
        //             echo " create folders and backup the jar and configs..."
        //             ssh -q -o StrictHostKeyChecking=no root@${host} 'mkdir -p ${backupLocation}/mdm-batch_$(date +%Y-%m-%d)' 

        //             ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/cp ${deploymentLocation}/${artifactName}.${artExtension} ${backupLocation}/mdm-batch_$(date +%Y-%m-%d)/' 
        //             ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/cp ${deploymentLocation}/${artifactName}.conf ${backupLocation}/mdm-batch_$(date +%Y-%m-%d)/' 
        //         else
        //             echo " "
        //             echo " ${artifactName} does not exist. run again and select no backup or investigate the problem"
        //             echo " "
        //         fi
        //     else
        //     echo "Skipping Backup"
        //     fi
        // """

        // Delete old files
        sh """
            echo "change permissions before deleteing files..."
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chattr -i ${deploymentLocation}/${artifactName}.${artExtension}' || echo 'skipping'
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chattr -i ${deploymentLocation}/${artifactName}.conf' || echo 'skipping'
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chown -R root:root ${deploymentLocation}/${artifactName}.${artExtension}' || echo 'skipping'
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chown -R root:root ${deploymentLocation}/${artifactName}.conf' || echo 'skipping'

            echo " delete jar and ${serviceName} folder...."
            ssh -q -o StrictHostKeyChecking=no root@${host} 'whoami' 
            ssh -q -o StrictHostKeyChecking=no root@${host} 'rm -f ${deploymentLocation}/${artifactName}.${artExtension}' 
            ssh -q -o StrictHostKeyChecking=no root@${host} 'rm -rf ${deploymentLocation}/${serviceName}' 

            echo "deleting configuration files..."
            ssh -q -o StrictHostKeyChecking=no root@${host} 'rm -f ${deploymentLocation}/${artifactName}.conf'

        """

        // Copy Config and Artifact
        // TODO: line 154 is causing the error; doesn't like copy + rename oneliner
        sh """
            echo "copying configuration files...."
            scp -q config/versions/${configVersion}/${targetEnv}/${artifactName}.conf root@${host}:${deploymentLocation}/

            echo "Copying ${artifactName}-${artifactVersion}.${artExtension} file..."
            scp -q ${artifactName}-${artifactVersion}.${artExtension} root@${host}:${deploymentLocation}/
            ssh -q -o StrictHostKeyChecking=no root@${host} 'mv ${deploymentLocation}/${artifactName}-${artifactVersion}.${artExtension} ${deploymentLocation}/${artifactName}.${artExtension}'
        """

        // Change permissions and ownership
        sh """
            sleep 2s
            echo "setting ownership..."
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chown -R ${user}:${group} ${deploymentLocation}/' 
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chown -R ${user}:${group} ${deploymentLocation}/logs' 
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chown -R ${user}:${group} ${deploymentLocation}/${artifactName}.conf' 

            echo "setting permissions..."
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chmod -R 500  ${deploymentLocation}/${artifactName}.${artExtension}' 
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chmod -R 400  ${deploymentLocation}/${artifactName}.conf' 
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chattr +i ${deploymentLocation}/${artifactName}.${artExtension}' 
            ssh -q -o StrictHostKeyChecking=no root@${host} '/bin/chattr +i ${deploymentLocation}/${artifactName}.conf' 

        """

        // Start the service
        sh """
            echo "Starting the service...."
            ssh -q -o StrictHostKeyChecking=no root@${host} '/sbin/service ${serviceName} start'
        """
    }
}
