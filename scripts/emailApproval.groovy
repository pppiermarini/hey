
import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.transform.Field
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _


node('linux2') {
    
    stage('send email'){
        
  wrap([$class: 'BuildUser']) {
    def user = env.BUILD_USER_ID
  }
        echo "${ env.BUILD_USER_ID}"
        echo "puppy"
        emailext mimeType: 'text/html',
                 subject: "[Jenkins]${currentBuild.fullDisplayName}",
                 to: "ppiermarini@incomm.com vhari@incomm.com rkale@incomm.com jrivett@incomm.com dstovall@incomm.com",
                 body: '''Please Approve Production Deployment for Holiday Season:
                 
                      <br><br><b><a href="${BUILD_URL}input">click to approve or abort</a></b></br></br>'''

        def userInput = input id: 'userInput',
                              message: 'Let\'s promote?', 
                              submitterParameter: 'submitter',
                              submitter: 'tom',
                              parameters: [
                                [$class: 'TextParameterDefinition', defaultValue: 'dev', description: 'Environment', name: 'env'],
                                [$class: 'TextParameterDefinition', defaultValue: 'prod', description: 'Target', name: 'target']]

        echo ("Env: "+userInput['env'])
        echo ("Target: "+userInput['target'])
        echo ("submitted by: "+userInput['submitter'])
        
    }
}
        
        