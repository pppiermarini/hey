import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

gitRepository = "https://github.com/InComm-Software-Development/cfes-tms-aml-explanation.git" 
//gitBranch = "${Branch}"
gitCreds = "scm-incomm"
BRANCH_SCOPE = ""
projectName = "cfes-tms-aml-explanation"

node('linux') {
    try {
    	cleanWs()

		stage('checkout'){
			sh """git clone ${gitRepository}
			cd ${WORKSPACE}/${projectName}
			git branch -r > branch.txt
			sed '1d' branch.txt > tmpfile
			sed -i '1d' branch.txt
			"""

			//sh 'git branch > branch.txt'
	//sh 'git tag -l | awk \'{print $1}\' ORS=\'\\n\' >> tag.txt'
			listBranches = readFile "${WORKSPACE}/${projectName}/branch.txt"
	//listTag = readFile 'tag.txt'
			echo "please click on the link here to choose a branch"
			timeout(time: 45, unit: 'SECONDS') {
				env.BRANCH_SCOPE = input message: 'Please choose the branch to build ', ok: 'Ok',
				parameters: [choice(name: 'BRANCH_NAME', choices: "${listBranches}", description: 'Branch to build?')]
			}

		}

		stage('git checkout w/Branch') {
			cleanWs()
			githubCheckout(gitCreds, gitRepository, "${env.BRANCH_SCOPE}")
		}


    } catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    } finally {
    //Sending a bunch of information via email to the email distro list of participants	
    echo "Got to finally"
	}
} //end of node