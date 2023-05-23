#!/bin/bash

#########################################################################################
# Creates a normal change after reading input json present in relative application folder.
# Uploads attachments and move the change to scheduled state.
#
# Parameters
# $1 - Application Name
# $2 - Application Version
# $3 - InComm Payments SNOW Environment
# $4 - SNOW Auth Header
# $5 - MS Teams Notification URL
# $6 - Office365 Email Webhook URL
#########################################################################################

# Variables and constants used
normal_change_base_url="https://incomm$3.service-now.com/api/sn_chg_rest/v1/change/normal"
attachment_upload_base_url="https://incomm$3.service-now.com/api/now/v1/attachment/file?table_name=change_request&table_sys_id=XXX&file_name="
get_attachments_base_url="https://incomm$3.service-now.com/api/now/v1/table/sys_attachment?sysparm_query=table_sys_id="
download_attachment_base_url="https://incomm$3.service-now.com/sys_attachment.do?sys_id="
copy_risk_assesment_url="https://incomm$3.service-now.com/api/sn_chg_rest/v1/change/risk_copy_asmt"
auto_approve_base_url="https://incomm$3.service-now.com/api/sn_chg_rest/v1/change/"
view_change_base_url="https://incomm$3.service-now.com/change_request.do?sys_id="
input_file_name="./applications/$1/releases/$2/create_change_details_request.json"
output_file_name="./applications/$1/releases/$2/create_change_details_response.json"
inputs_valid="true"
output_folder="./applications/$1/releases/$2/"
auth_header="Authorization: Basic $4"
ms_teams_webhook_url="$5"
office365_email_webhook_url="$6"
app_metadata_json="./applications/$1/app-metadata.json"
number=""
sys_id=""
short_description=""
current_state=""
change_details=""
app_metadata=""

# Shell script imports
source ./scripts/dateUtils.sh
source ./scripts/msTeamsNotificationUtils.sh
source ./scripts/office365EmailUtils.sh

# Functions
function validate_inputs() {
    if [[ -z "$1" || "$1" == "" ]]; then
        echo -e "Application Name is null or empty"
        inputs_valid="false"
    fi

    if [[ -z "$2" || "$2" == "" ]]; then
        echo -e "Application Version is null or empty"
        inputs_valid="false"
    fi

    if [[ -z "$3" || "$3" == "" ]]; then
        echo -e "InComm Payments SNOW Environment is null or empty"
        inputs_valid="false"
    fi

    if [[ -z "$4" || "$4" == "" ]]; then
        echo -e "SNOW Auth Header is null or empty"
        inputs_valid="false"
    fi

    if [[ -z "$5" || "$5" == "" ]]; then
        echo -e "MS Teams URL is null or empty"
        inputs_valid="false"
    fi

    if [[ -z "$6" || "$6" == "" ]]; then
        echo -e "Office365 Email Webhook URL is null or empty"
        inputs_valid="false"
    fi

    if [[ "$3" != "dev" && "$3" != "sand" && "$3" != "test" && "$3" != "qa" && "$3" != "prod" ]]; then
        echo -e "InComm Payments SNOW Environment is not valid"
        inputs_valid="false"
    fi

    # Check if input file exists
    if [ ! -s "$input_file_name" ]; then
        echo -e "$input_file_name does not exist or is empty"
        inputs_valid="false"
    fi

    # Check if output file exists
    if [ -s "$output_file_name" ]; then
        echo -e "$output_file_name exists and so we cannot create a Normal change"
        inputs_valid="false"
    fi

    # Validate if requested times in UTC defined in change request file is in future
    change_request="$(cat "$input_file_name")"
    start_time=$(echo "$change_request" | jq -r '.u_req_imp_start_date')
    end_time=$(echo "$change_request" | jq -r '.u_req_imp_end_date')

    stDtValid=$(is_date_valid "$start_time")
    enDtValid=$(is_date_valid "$end_time")
    currentDateTime=$(get_current_formatted_utc_date)

    if [[ "1" == "$stDtValid" || "1" == "$enDtValid" ]]; then
        echo -e "Requested start time or end time is not valid!"
        inputs_valid="false"
    fi
    echo "Current UTC time $currentDateTime Requested UTC start_time $start_time and end_time $end_time"
    validStartDt=$(end_time_greater_than_start_time "$currentDateTime" "$start_time")
    validEndDt=$(end_time_greater_than_start_time "$currentDateTime" "$end_time")
    validReqDts=$(end_time_greater_than_start_time "$start_time" "$end_time")

    if [[ "1" == "$validStartDt" || "1" == "$validEndDt" || "1" == "$validReqDts" ]]; then
        echo -e "Requested start time or end time has to be in the future and end time has to be greater than start time!"
        inputs_valid="false"
    fi

    # Special handling for prod environment
    # replace prod with empty string in url
    if [ "$3" == "prod" ]; then
        normal_change_base_url=${normal_change_base_url//prod/}
        attachment_upload_base_url=${attachment_upload_base_url//prod/}
        get_attachments_base_url=${get_attachments_base_url//prod/}
        download_attachment_base_url=${download_attachment_base_url//prod/}
        copy_risk_assesment_url=${copy_risk_assesment_url//prod/}
        auto_approve_base_url=${auto_approve_base_url//prod/}
        view_change_base_url=${view_change_base_url//prod/}
    fi
}
 
function create_normal_change_request() {
    # Invoke SNOW API to create a Normal change
    change_details=$(curl --silent --request POST --url "$normal_change_base_url" --header "$auth_header" --header 'Content-Type: application/json' --header 'Accept: application/json' --data "@$input_file_name")
        
    # Format the JSON file
    echo "$change_details"  | jq '.' > create_change_details_response.json

    # Get change number from response
    number=$(jq -r '.result.number.value' create_change_details_response.json)

    # Get change sys_id from response
    sys_id=$(jq -r '.result.sys_id.value' create_change_details_response.json)

    # Get change short_description from response
    short_description=$(jq -r '.result.short_description.value' create_change_details_response.json)

    # Get change state from response
    current_state=$(jq -r '.result.state.display_value' create_change_details_response.json)
    
    if [ "${#sys_id}" == 32 ]; then 
        printf "Step 1: Successfully created a Normal Change request with number [%s], sys_id [%s], short description [%s] and state [%s]\n\n" "$number" "$sys_id" "$short_description" "$current_state"
    fi

    send_create_normal_change_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url"
}

function upload_change_attachments() {
    printf "Step 2: Uploading available attachments to Normal Change Request [%s] on state [%s]\n\n" "$number" "$current_state"
    attachment_upload_base_url=${attachment_upload_base_url/XXX/$sys_id}
    local has_attachments="false"

    for entry in "$output_folder"*
    do
        # Skip the metadata files
        if [[ $entry != *"create_change_details_re"* ]]; then
            upload_file "$entry"
            has_attachments="true"
        fi
    done

    if [ "$has_attachments" == "true" ]; then
        get_normal_change_details
        attachments=$(curl --silent --show-error --request GET --url "$get_attachments_base_url$sys_id" --header "$auth_header")
        send_upload_attachments_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url" "$download_attachment_base_url" "$attachments"
    fi
}

function upload_file() {
    local full_file_name="$1"
    local file_name=file_mime_type=encoded_file_name
    file_name=$(basename "$full_file_name")
    file_mime_type=$(file -b --mime-type "$full_file_name")
    encoded_file_name=$(jq -R -r @uri <<<"$file_name")

    # Invoke SNOW API to upload attachment to a Normal change
    curl --silent --request POST \
        --url "$attachment_upload_base_url$encoded_file_name" \
        --header "$auth_header" \
        --header "Content-Type: $file_mime_type" \
        --data-binary "@$full_file_name" \
        -output /dev/null

    printf "\n\t---> Attached [%s] successfully to Normal Change Request [%s]\n\n" "$file_name" "$number"
}

function copy_risk_assessment() {
    app_metadata="$(cat "$app_metadata_json")"
    template_change=$(echo "$app_metadata" | jq -r '.template_change_number')
    printf "Step 3: Copying Risk Assessment to Normal Change Request [%s] from template change [%s] on state [%s]\n\n" "$number" "$template_change" "$current_state"

    local rawData='{"change":"'$number'", "template":"'$template_change'", "user":"svc_sop_devops"}'
    # Invoke SNOW API to copy risk assessment
    curl --silent \
        --request POST \
        --url "$copy_risk_assesment_url" \
        --header "$auth_header" \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        --data "$rawData" \
        --output /dev/null

    get_normal_change_details
    send_copy_risk_assessment_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url"
}

function update_change_request() {
    local future_state="$1"
    printf "Step 4: Updating state to [%s] for Normal Change Request [%s] from state [%s]\n\n" "$1" "$number" "$current_state"

    local rawData='{"state":"'$future_state'"}'
    # Invoke SNOW API to copy risk assessment
    curl --silent \
        --request PATCH \
        --url "${normal_change_base_url}/${sys_id}" \
        --header "$auth_header" \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        --data "$rawData" \
        --output /dev/null
    
    current_state="$future_state"

    get_normal_change_details
    send_update_normal_change_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url"
}

function auto_approve_all() {
    printf "Step 5: Auto approving all for Normal Change Request [%s] from state [%s]\n\n" "$number" "$current_state"

    local rawData='{"state":"approved", "user":"all", "comments":"Change approved by all approvers per automated change approval process."}'
    # Invoke SNOW API to copy risk assessment
    curl --silent \
        --request PATCH \
        --url "${auto_approve_base_url}${sys_id}/approvals" \
        --header "$auth_header" \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        --data "$rawData" \
        --output /dev/null
    
    get_normal_change_details
    send_approve_all_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url"
}

function auto_approve_sonja_goodwin() {
    printf "Step 6: Auto approving Sonja Goodwin's approval for Normal Change Request [%s] on state [%s]\n\n" "$number" "$current_state"

    local rawData='{"state":"approved", "user":"d617a38a0f551a40c745b65be1050e8d", "comments":"Change approved by Sonja Goodwin as per automated change approval process."}'
    # Invoke SNOW API to copy risk assessment
    curl --silent \
        --request PATCH \
        --url "${auto_approve_base_url}${sys_id}/approvals" \
        --header "$auth_header" \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        --data "$rawData" \
        --output /dev/null
    
    get_normal_change_details
    send_approve_change_management_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url"
}

function get_normal_change_details() {
    # Invoke SNOW API to get current change details
    change_details=$(curl --silent --show-error --request GET --url "$normal_change_base_url/$sys_id" --header "$auth_header")
    current_state=$(echo "$change_details" | jq -r '.result.state.display_value')
    
    # Format the output and overwrite JSON file
    echo "$change_details"  | jq '.' > create_change_details_response.json
    
    printf "\t---> Normal Change Request [%s] current state is [%s]\n\n" "$number" "$current_state"
}

function commit_response_file_to_git() {        
    # Move the response JSON to the appropriate repository folder to commit it back
    mv create_change_details_response.json "$output_folder"
    
    # Get the latest changes
    git pull
    
    # Commit the file back to the repo
    git config user.name github-actions
    git config user.email github-actions@github.com
    git add "${output_folder}"create_change_details_response.json
    git commit -m "$number change response commit from github action workflow"
    git push origin main
    
    printf "Step 7: Change details response file [create_change_details_response.json] for Normal Change Request [%s] committed back to Git repo\n\n" "$number"
}

# Validate inputs and create a normal change if valid
validate_inputs "$1" "$2" "$3" "$4" "$5" "$6"

if [ $inputs_valid == "true" ]; then
    printf "\n\n\n[Creating a Normal Change request for [%s] and version [%s] using SNOW [%s] Rest API.]\n\n\n" "$1" "$2" "$3"
    create_normal_change_request
    if [ "${#sys_id}" == 32 ]; then 
        upload_change_attachments
        copy_risk_assessment
        update_change_request "review"
        auto_approve_all
        sleep 10
        auto_approve_sonja_goodwin
        sleep 20
        auto_approve_all
        sleep 30
        get_normal_change_details
        send_update_normal_change_notification "$change_details" "$ms_teams_webhook_url" "$view_change_base_url"
        send_normal_change_scheduled_email "$change_details" "$office365_email_webhook_url" "$view_change_base_url" "$1"
        commit_response_file_to_git
    else
        printf "\n\nUnable to create Normal change request !!\n\n"
    fi
    
else 
    printf "\n\nInputs are invalid and so aborting normal change request creation!!\n\n"
fi

