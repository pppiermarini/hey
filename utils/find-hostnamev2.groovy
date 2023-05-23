import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

emailDistribution = "vhari@incomm.com, dstovall@incomm.com, ppiermarini@incomm.com, jrivett@incomm.com"

tout = "${TIME_OUT}"
sourceNode = "${SOURCE_NODE}"

targets = [
    '10.42.17.242',
    '10.42.16.181',
    '10.42.16.183',
    '10.42.17.244',
    '10.42.17.245',
    '10.42.17.246',
    '10.42.17.247',
    '10.42.82.107',
    '10.42.82.108',
    '10.42.82.109',
    '10.42.84.113',
    '10.42.81.160',
    '10.42.81.163',
    '10.42.82.221',
    '10.42.82.223',
    '10.42.82.217',
    '10.42.82.215',
    '10.42.48.135',
    '10.42.48.136',
    '10.42.48.132',
    '10.42.48.134',
    '10.42.84.113',
    '10.42.84.112'
]

node(sourceNode){
    try {  
        stage('Check hostnames'){
            cleanWs()
            sh "touch ${env.WORKSPACE}/hosts.txt"
            sshagent(["scm_deployment"]) {
                targets.each { target ->
                    timeout (time: tout, unit: 'SECONDS') {
                        sh """
                            entry="${target}: " 
                            entry+=`(ssh -q -o StrictHostKeyChecking=no root@${target} 'hostname')` || entry+='Not found within timeout.'
                            entry+='\n'
                            echo \$entry >> ${env.WORKSPACE}/hosts.txt 
                        """
                    }
                }
                echo "HOSTNAME SEARCH RESULTS:"
                echo "======================================================"
                sh "cat ${env.WORKSPACE}/hosts.txt"
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
}
