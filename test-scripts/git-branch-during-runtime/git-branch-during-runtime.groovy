import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

node('linux'){
    try {  
        // GH Branch Param Runtime Test
        stage('select-branch') {
            branch = input( 
                id: 'userInput',
                parameters: [[
                        $class: 'GithubBranchParameterDefinition',
                        githubRepoUrl: "https://github.com/InComm-Software-Development/mdm-api-service"
                    ]]
            )
            println("You Selected: ${branch}")
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