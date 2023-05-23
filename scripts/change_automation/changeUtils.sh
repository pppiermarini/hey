#!/bin/bash

############################################################################
# Contains various functions to invoke various change management functions
#
# Parameters
# $1 - Operation to invoke. 1 for create change, 2 for verify change, 3 for close change
# $2 - Application Name
# $3 - Application Version
# $4 - InComm Payments SNOW Environment
# $5 - Change Operations Personal Access Token
# $6 - Deployment Status - (successful or failed)
# $7 - Deplyment Start Time
# $8 - Deployment End Time
############################################################################

# Variables and constants used
trigger_normal_change_creation_url="https://api.github.com/repos/InComm-Software-Development/sop-apps-change-management/actions/workflows/create-normal-change.yml/dispatches"
trigger_normal_change_closing_url="https://api.github.com/repos/InComm-Software-Development/sop-apps-change-management/actions/workflows/close-normal-change.yml/dispatches"
create_change_inputs_valid="true"
verify_change_inputs_valid="true"
close_change_inputs_valid="true"
change_created_and_scheduled="false"

# Functions
function validate_normal_change_creation_inputs() {
    if [[ -z "$1" || "$1" == "" ]]; then
        echo -e "Application Name is null or empty"
        create_change_inputs_valid="false"
    fi

    if [[ -z "$2" || "$2" == "" ]]; then
        echo -e "Application Version is null or empty"
        create_change_inputs_valid="false"
    fi

    if [[ -z "$3" || "$3" == "" ]]; then
        echo -e "InComm Payments SNOW Environment is null or empty"
        create_change_inputs_valid="false"
    fi

    if [[ -z "$4" || "$4" == "" ]]; then
        echo -e "SNOW Auth Header is null or empty"
        create_change_inputs_valid="false"
    fi

    if [[ "$3" != "dev" && "$3" != "sand" && "$3" != "test" && "$3" != "qa" && "$3" != "prod" ]]; then
        echo -e "InComm Payments SNOW Environment is not valid"
        create_change_inputs_valid="false"
    fi
}

function trigger_normal_change_creation() {
  echo "Creating a normal change for [$1] application deployment version [$2] in SNOW [$3] ENV"
  local rawData='{"ref": "main", "inputs": {"application-name": "'$1'","release-version": "'$2'","snow-env": "'$3'"}}'
  curl --location --request POST "$trigger_normal_change_creation_url" \
       --header 'Accept: application/vnd.github.v3+json' \
       --header 'Content-Type: application/json' \
       --header "Authorization: Bearer $4" \
       --data "$rawData"
}

function validate_normal_change_scheduled_inputs() {
    if [[ -z "$1" || "$1" == "" ]]; then
        echo -e "Application Name is null or empty"
        verify_change_inputs_valid="false"
    fi

    if [[ -z "$2" || "$2" == "" ]]; then
        echo -e "Application Version is null or empty"
        verify_change_inputs_valid="false"
    fi

    if [[ -z "$3" || "$3" == "" ]]; then
        echo -e "InComm Payments SNOW Environment is null or empty"
        verify_change_inputs_valid="false"
    fi

    if [[ -z "$4" || "$4" == "" ]]; then
        echo -e "SNOW Auth Header is null or empty"
        verify_change_inputs_valid="false"
    fi

    if [[ "$3" != "dev" && "$3" != "sand" && "$3" != "test" && "$3" != "qa" && "$3" != "prod" ]]; then
        echo -e "InComm Payments SNOW Environment is not valid"
        verify_change_inputs_valid="false"
    fi
}

function verify_normal_change_scheduled() {
    echo "Verifying change creation for deployment of [$1] application version [$2] in SNOW [$3] ENV"
    curl --silent --header 'Accept: application/vnd.github.v3.raw' \
         --header "Authorization: token $4" \
         "https://raw.githubusercontent.com/InComm-Software-Development/sop-apps-change-management/main/applications/$1/releases/$2/create_change_details_response.json" \
        2> err.log > output.json
    # Verify response file exists and the state is scheduled.
    if [ -s output.json ]; then  
        change_details=$(cat output.json)
        # Check if string contains sys_id
        if [[ ! "$change_details" = *sys_id* ]]; then
            printf "\nChange details are invalid !!\n"
        else
            # Get change number from response
            change_number=$(echo "$change_details" | jq -r '.result.number.value')
            # Get change sys_id from response
            sys_id=$(echo "$change_details" | jq -r '.result.sys_id.value')
            # Get change state from response
            current_state=$(echo "$change_details" | jq -r '.result.state.display_value')
            if [[ "${#sys_id}" == 32 && "$current_state" == "Scheduled" ]]; then
                printf "\nChange [%s] is valid and is in Scheduled state !!\n" "$change_number"
                change_created_and_scheduled="true"
            else
                printf "\nsys_id is not valid or change is not in Scheduled state !!\n"
            fi
        fi                
       
    else
        printf "\nUnable to load and verify Normal change request !!\n"
    fi
}

function validate_normal_change_closing_inputs() {
    if [[ -z "$1" || "$1" == "" ]]; then
        echo -e "Application Name is null or empty"
        close_change_inputs_valid="false"
    fi

    if [[ -z "$2" || "$2" == "" ]]; then
        echo -e "Application Version is null or empty"
        close_change_inputs_valid="false"
    fi

    if [[ -z "$3" || "$3" == "" ]]; then
        echo -e "InComm Payments SNOW Environment is null or empty"
        close_change_inputs_valid="false"
    fi

    if [[ -z "$4" || "$4" == "" ]]; then
        echo -e "SNOW Auth Header is null or empty"
        close_change_inputs_valid="false"
    fi

    if [[ "$3" != "dev" && "$3" != "sand" && "$3" != "test" && "$3" != "qa" && "$3" != "prod" ]]; then
        echo -e "InComm Payments SNOW Environment is not valid"
        close_change_inputs_valid="false"
    fi

    if [[ -z "$5" || "$5" == "" ]]; then
        echo -e "Application deployment status is null or empty"
        close_change_inputs_valid="false"
    fi

    if [[ "$5" != "successful" && "$5" != "failed" ]]; then
        echo -e "Application deployment status is not valid"
        close_change_inputs_valid="false"
    fi

    if [[ -z "$6" || "$6" == "" ]]; then
        echo -e "Application deployment start time is null or empty"
        close_change_inputs_valid="false"
    fi

    if [[ -z "$7" || "$7" == "" ]]; then
        echo -e "Application deployment end time is null or empty"
        close_change_inputs_valid="false"
    fi
}

function trigger_normal_change_closing() {
  echo "Closing normal change for [$1] application deployment version [$2] in SNOW [$3] ENV "
  echo "Deployment Status: $5"
  echo "Deployment Work Start Time: $6"
  echo "Deployment Work End Time: $7"
  local rawData='{"ref": "main", "inputs": {"application-name": "'$1'","release-version": "'$2'","snow-env": "'$3'","deployment_status": "'$5'","deployment_start_time": "'$6'","deployment_end_time": "'$7'"}}'
  curl --location --request POST "$trigger_normal_change_closing_url" \
       --header 'Accept: application/vnd.github.v3+json' \
       --header 'Content-Type: application/json' \
       --header "Authorization: Bearer $4" \
       --data "$rawData"
}

# Choose operation based on the first argument passed in
# and write output to file to be read later
if [ "$1" == "1" ]; then
    # Validate arguments for create change
    validate_normal_change_creation_inputs "$2" "$3" "$4" "$5"
    # Create change
    if [ $create_change_inputs_valid == "true" ]; then
        trigger_normal_change_creation "$2" "$3" "$4" "$5"
    else
        echo "The create change inputs are not valid"
    fi    
    echo "$create_change_inputs_valid" > create_change_result.txt
elif [ "$1" == "2" ]; then
    # Validate arguments for verify change
    validate_normal_change_scheduled_inputs "$2" "$3" "$4" "$5"
    # Verify change
    if [ $verify_change_inputs_valid == "true" ]; then
        verify_normal_change_scheduled "$2" "$3" "$4" "$5"
        echo "$change_created_and_scheduled" > verify_change_result.txt
    else
        echo "The verify change inputs are not valid"        
        echo "$verify_change_inputs_valid"  > verify_change_result.txt
    fi
elif [ "$1" == "3" ]; then
    # Validate arguments for close change
    validate_normal_change_closing_inputs "$2" "$3" "$4" "$5" "$6" "$7" "$8"
    # Close change if valid
    if [ $close_change_inputs_valid == "true" ]; then
        trigger_normal_change_closing "$2" "$3" "$4" "$5" "$6" "$7" "$8"
    else
        echo "The close change inputs are not valid"
    fi
    echo "$close_change_inputs_valid" > close_change_result.txt
else
    # Invalid option
    echo "The operation requested is not valid. The first argument has to be 1 (create change) or 2 (verify change) or 3 (close change)"
    echo "false"
fi
