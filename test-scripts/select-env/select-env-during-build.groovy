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

// Pipeline Globals
emailDistribution = "vhari@incomm.com, dstovall@incomm.com, ppiermarini@incomm.com, rkale@incomm.com, jrivett@incomm.com"
targets = null

// Defines the target environment ID's for a given application
targetIDs_lle = [
    'app-0': """
        dev-a,
        qa-a,
        qa-b
    """,
    
    'app-1': """
        dev-b,
        dev-c,
        dev-d,
        qa-c
    """
]

targetIDs_hle = [
    'app-0': """
        prod-a,
        prod-b,
        prod-c
    """,
    
    'app-1': """
        prod-d,
        prod-e,
        prod-f
    """
]

// Defines the IP's for a given target environment ID
targetAddresses = [
    'dev-a': '123.123.123.001',
    'dev-b': '123.123.123.002',
    'dev-c': '123.123.123.003',
    'dev-d': '123.123.123.004',
    'dev-e': '123.123.123.005',
    'dev-f': '123.123.123.006',

    'qa-a': '456.456.456.001',
    'qa-b': '456.456.456.002',
    'qa-c': '456.456.456.003',
    'qa-d': '456.456.456.004',
    'qa-e': '456.456.456.005',
    'qa-f': '456.456.456.006',


    'prod-a': 'spscmapp.incomm.com',
    'prod-b': '111.111.111.002',
    'prod-c': '111.111.111.003',
    'prod-d': '111.111.111.004',
    'prod-e': '111.111.111.005',
    'prod-f': '111.111.111.006'
]

node('linux'){
    try {  

        stage('define-targets') {
            defineTargets()
        }

        // First, User selects an Environment ID 
        stage('select-target') {
            targets = input( 
                id: 'userInput',
                parameters: [extendedChoice(
                    name: 'TARGET_SELECTION',
                    type: 'PT_MULTI_SELECT',
                    value: targets[appID],
                    description: 'Select a value'
                )]
            )
            targets = targets.split(",")
            println("You Selected: ${targets}")
        }

        // Then, display the corresponding addresses registered to that Environment ID
        stage('display-selected-target') {
            targets.each{ target ->
                println("${target}'s addresses: ${targetAddresses[target]}")
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

def defineTargets() {
    println("Full Name: ${JOB_NAME}")
    parentFolder = "${JOB_NAME}".split("/")[0].toLowerCase()
    println("Parent Folder: " + parentFolder)
	
    switch(parentFolder.toLowerCase()){
        case "lle":
            echo "LLE env deploy"
            targets = targetIDs_lle
            echo "Current targets: ${targets}"
            break

        case "hle":
            echo "HLE env deploy"
            targets = targetIDs_hle
            echo "Current targets: ${targets}"
            break

        default:
            throw new Exception("No Application Targets found for the defined application under FSAPI-DataDriven-LLE folder")
            break
    }
}