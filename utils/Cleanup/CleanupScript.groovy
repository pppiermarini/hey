import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

allJobs = "${USERINPUT}"
items = allJobs.split(",")
sanitizedItems = []

sanitizedItemsFilter = []
final String test_instance = "http://10.42.97.153:8080"
//final String build_instance = "https://build.incomm.com"
//final String fs_instance = "https://build-fs.incomm.com"

/* withCredentials() {
    // curl -XPOST 'http://jenkins/job/FolderName/doDelete' --user 'user.name:YourAPIToken'
}
withCredentials([usernamePassword(credentialsId: 'jenkins-api', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
    for item in items {
        sh """curl -XPOST '${test_instance}/job/${item}/doDelete' --user '${USER}:${PASS}'"""
    }
} */


node('linux'){
	try { 
		cleanWs()
		stage('Filtering Inputs') {
            for (String item : items) {
                //echo item
                sanitizedItems.add(item.replaceAll("\\s", "%20"))
            }
            // echo "${sanitizedItems}"

            for(String sanitizedItem : sanitizedItems) {
                if (sanitizedItem.startsWith("%20")) {
                    sanitizedItemsFilter.add(sanitizedItem.substring(3)) 
                }
                else {       
                sanitizedItemsFilter.add(sanitizedItem)
                }
		}
        echo "${sanitizedItemsFilter}"
    }

        stage('Making API Call') {
            withCredentials([usernamePassword(credentialsId: 'jenkins-api', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                for (String item : sanitizedItemsFilter) {
                    sh """curl -XPOST '${test_instance}/job/${item}/doDelete' --user '${USER}:${PASS}'"""
                    //sh """curl -XGET '${test_instance}/checkJobName?value=${item}' --user '${USER}:${PASS}'"""
                }
            }   
        }		

	} //try 
		
catch (Exception e) {
        echo "ERROR: ${e.toString()}"
		currentBuild.result = "FAILURE"
    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
    echo "Made it to the end."
    }
		
}  //end of node