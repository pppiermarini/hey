import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

emailDistribution = "jrivett@incomm.com vhari@incomm.com dstovall@incomm.com rkale@incomm.com ppiermarini@incomm.com"
//emailDistribution = "jrivett@incomm.com"

//allJobs = "${USERINPUT}"
//items = allJobs.split(",")
items = ""
sanitizedItems = []

sanitizedItemsFilter = []
final String test_instance = "http://10.42.97.153:8080"
final String build_instance = "https://build.incomm.com:443"
//final String fs_instance = "https://build-fs.incomm.com"

gitRepository="https://github.com/InComm-Software-Development/v3-pipeline-scripts.git"
gitBranch="origin/development"
gitCreds="scm-incomm"

listFile = 'utils/Cleanup/data/dwyanesjobs.csv'

node('linux'){
	try { 
		cleanWs()

        stage('Checkout') {
            githubCheckout(gitCreds,gitRepository,gitBranch)
        }

        stage('Read CSV') {
            items = readCSV file: listFile
            println items
            if (items.isEmpty()) {
                throw new Exception("Please check the csv name or add the csv file")

            }
            
        }
        
		stage('Filtering Inputs') {
            for (String item : items) {
                 
                sanitizedItems.add(item[0].replaceAll("\\s", "%20"))
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
            withCredentials([usernamePassword(credentialsId: 'jrivett-incomm', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                for (String item : sanitizedItemsFilter) {
                    sh """curl -XPOST '${build_instance}/job/${item}/doDelete' --user '${USER}:${PASS}'"""
                    //sh """curl -XGET '${build_instance}/checkJobName?value=${item}' --user '${USER}:${PASS}'"""
                }
            }   
        }

	} //try 
		
    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        currentBuild.result = "FAILURE"
    } 
    finally {
        //Sending a bunch of information via email to the email distro list of participants	
        sendEmailCleanup(emailDistribution, listFile)
        echo "Made it to the end."
    }
		
}  //end of node