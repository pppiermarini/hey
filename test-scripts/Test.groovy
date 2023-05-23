import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*


@Library('pipeline-shared-library') _

credsName = "scm_deployment"

gitCreds="scm-incomm"
gitRepositoryDepOne="https://github.com/InComm-Software-Development/mdm-webapp.git"
branchOne="${BRANCHDEPONE}"


gitRepositoryDepTwo="https://github.com/InComm-Software-Development/mdm-cache-daemon.git"
branchTwo="${BRANCHDEPTWO}"

gitRepositoryDepThree="https://github.com/InComm-Software-Development/mdm-swipe-reload.git"
branchThree="${BRANCHDEPTHREE}"

//globals


now = new Date()
nowFormatted = now.format("YYYYMMdd_HHmmss", TimeZone.getTimeZone('UTC'))

node('linux'){
	try { 
		cleanWs()
		//select the artifact 
		stage('Dependency One'){
				githubCheckout(gitCreds,gitRepositoryDepOne,branchOne)
				sh """
				ls -lt
				"""
				echo "Starting Job two with Branch: ${branchTwo}"

			}

		stage('Dependency Two') {
			build job: 'Git-Tag-Checkout-Another', parameters: [listGitBranches(name: 'BRANCHDEPTWO', value: "${branchTwo}")]

		}
		
		stage('Dependency Three') {
			build job: 'Git-Tag-Checkout-Three', parameters: [listGitBranches(name: 'BRANCHDEPTHREE', value: "${branchThree}")]

		}


		
		
	} catch (exc) {

			echo 'ERROR:  '+ exc.toString()
			throw exc
		
	} finally {

		echo 'In Finally!, ie good!'
	}

} //end of node


///// The End