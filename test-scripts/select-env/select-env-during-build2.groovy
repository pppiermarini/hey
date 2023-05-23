import hudson.model.*
import hudson.console.HyperlinkNote
import hudson.AbortException
import groovy.xml.MarkupBuilder
import groovy.xml.*
import groovy.util.*
import groovy.json.*

@Library('pipeline-shared-library') _

// JENKINS UI PARAMS
appID = "${APP_ID}"

targetEnv="${SRC_PROPERTY_ENV}"
// Pipeline Globals
emailDistribution = "vhari@incomm.com, dstovall@incomm.com, ppiermarini@incomm.com, rkale@incomm.com, jrivett@incomm.com"


targetIDs = [
	'FSAPI_Security_SBC3': """ 
		dev-a,
        dev-b,
		qa-aa,
		qa-ba,
		uat,
		load
	""",
	'CCA_Get':""" 
		dev-a,
		qa-aa,
		qa-ba,
		uat
	""",
	'CCA_Action': """ 
		dev-a,
		qa-aa,
		qa-ba,
		uat
	"""
]

targetAddresses = [
    'dev-a': '10.42.17.242',
    'dev-b': '10.42.16.181',
    'dev-c': '10.42.16.183',
    'dev-d': '10.42.17.244',
    'dev-e': '10.42.17.245',
    'dev-f': '10.42.17.246',
	'dev-g': '10.42.17.247',


    'qa-aa': '10.42.82.107',
    'qa-ab': '10.42.82.108',
    'qa-ac': '10.42.82.109',
    'qa-ad': '10.42.84.113',

    'qa-ba': '10.42.81.160',
    'qa-bb': '10.42.81.163',
    'qa-bc': '10.42.82.221',
	'qa-bd': '10.42.82.223',
	'qa-be': '10.42.82.217',
	'qa-bf': '10.42.82.215',

	'uat-a': '10.42.48.135',
	'uat-b': '10.42.48.136',
	'uat-c': '10.42.48.132',
	'uat-d': '10.42.48.134',
	'uat-e': '10.42.84.113',
	'uat-f': '10.42.84.112'

]
targets = []
currentTargets = []
targetInputLabel =""
displayTargets = []
node('linux'){
    try {  
        /*
        stage('select-target') {
        
        echo "${targetIDs[APP_ID]}"
        targetAddresses.each {
            key,value -> 
            //println "Key:" + key + "Value:" + value
            if(targetIDs[APP_ID].contains(key)) {
                currentTargets.add(value)
            }
          
        }


        // shorten the currentTargets array based on the env, then we add the current targets a dropdown
        echo "${currentTargets}"
        currentTargets.each {val -> displayTargets.add(booleanParam(name: val, defaultValue: false))}
        targetInputLabel = input  message: "Please select targets for ${targetIDs[APP_ID]}", ok : 'Deploy',id :'target_id', parameters: displayTargets
        //echo "${targetInputLabel}"
        //echo "${targetInputLabel}".split(":")[0]

        targetInputLabel.each {
            val -> 
            if("${val}".split("=")[1] == 'true') {
                targets.add("${val}".split("=")[0])
            }
             
        }*/
       
        stage('select-target') {
        echo "${targetIDs[APP_ID]}"
        echo "${targetEnv}"
        targetAddresses.each {
            key,value -> 
            //println "Key:" + key + "Value:" + value
            
            if(targetIDs[APP_ID].contains(key) && key.contains(targetEnv)) {
                targets.add(value)
            }
          
        }
        echo "current targets: ${targets}"

        }
    }
    
    catch (Exception e) {
        echo "ERROR: ${e.toString()}"
        echo 'Something went wrong'
        currentBuild.currentResult = 'FAILURE'
    }

    finally {
        //Sending a bunch of information via email to the email distro list of participants
        echo "notify"	
        //sendEmailv3(emailDistribution, getBuildUserv1())	
	}
}
